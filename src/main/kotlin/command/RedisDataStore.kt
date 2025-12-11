package command

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.mutableMapOf
import kotlin.math.max
import kotlin.math.min

class RedisDataStore {

    private val lock = ReentrantLock()

    val streams = Streams()
    private val strings = mutableMapOf<String, StringEntry>()
    private val lists = mutableMapOf<String, ArrayDeque<String>>()
    private val lockConditions = mutableMapOf<String, Condition>()

    fun set(key: String, value: String, px: Long? = null) {
        val expiresAt = px?.let { System.currentTimeMillis() + it }

        strings[key] = StringEntry(value, expiresAt)
    }

    fun get(key: String): String? {
        val currentTime = System.currentTimeMillis()

        val entry = strings[key] ?: return null

        if (entry.isExpired(currentTime)) {
            strings.remove(key)
            return null
        }

        return entry.value
    }

    fun lpush(key: String, values: List<String>): Int {
        val list = lists.getOrPut(key) { ArrayDeque() }
        values.forEach { list.addFirst(it) }

        return list.size
    }

    fun rpush(key: String, values: List<String>): Int {
        lock.lock()
        try {
            val list = lists.getOrPut(key) { ArrayDeque() }
            list.addAll(values)

            lockConditions[key]?.signalAll()

            return list.size
        } finally {
            lock.unlock()
        }
    }

    fun lrange(key: String, start: Int, stop: Int): List<String> {
        val list = lists[key] ?: return emptyList()
        val length = list.size

        val normalizedStart = if (start < 0) {
            max(0, start + length)
        } else {
            start
        }

        val normalizedStop = if (stop < 0) {
            max(0, stop + length)
        } else {
            stop
        }

        if (normalizedStart > normalizedStop) {
            return emptyList()
        }

        if (normalizedStart >= length) {
            return emptyList()
        }

        return list.subList(normalizedStart, min(normalizedStop, length - 1) + 1)
    }

    fun llen(key: String): Int {
        return lists[key]?.size ?: 0
    }

    fun lpop(key: String, count: Int?): List<String> {
        val list = lists[key]
        if (list.isNullOrEmpty()) {
            return emptyList()
        }

        val length = list.size
        val normalizedCount = min(length, count ?: 1)

        return List(normalizedCount) { list.removeFirst() }
    }

    fun blpop(key: String, timeoutMilliSeconds: Long): String? {
        lock.lock()
        try {
            val list = lists.getOrPut(key) { ArrayDeque() }
            val condition = lockConditions.getOrPut(key) { lock.newCondition() }
            val timeoutNano = TimeUnit.MILLISECONDS.toNanos(timeoutMilliSeconds)
            val deadLineNano = System.nanoTime() + timeoutNano

            while (list.isEmpty()) {
                val remainingNano = deadLineNano - System.nanoTime()
                if (timeoutMilliSeconds == 0L) {
                    condition.await()
                    continue
                }

                if (remainingNano <= 0) {
                    return null
                }

                condition.awaitNanos(remainingNano)

            }

            return list.removeFirst()
        } finally {
            lock.unlock()
        }
    }

    fun type(key: String): String {
        val type = when {
            key in strings -> RedisDataType.STRING
            key in lists -> RedisDataType.LIST
            streams.contains(key) -> RedisDataType.STREAM
            else -> RedisDataType.NONE
        }

        return type.typeName
    }

    fun <R> atomic(operation: () -> R): R {
        lock.lock()
        return try {
            operation()
        } finally {
            lock.unlock()
        }
    }

    data class StringEntry(
        val value: String,
        val expiresAt: Long? = null
    ) {
        fun isExpired(currentTimestamp: Long): Boolean =
            expiresAt != null && expiresAt <= currentTimestamp
    }

    class Streams(
        private val sequenceTrackers: MutableMap<String, MutableMap<Long, AtomicLong>> = mutableMapOf(),
        private val entries: LinkedHashMap<String, LinkedHashMap<String, StreamEntry>> = linkedMapOf()
    ) {

        fun xadd(key: String, id: String, keyPairs: List<Pair<String, String>>): String {
            val stream = entries.getOrPut(key) { linkedMapOf() }
            val entry = stream.getOrPut(id) { StreamEntry(id, keyPairs) }

            return entry.id
        }

        fun xrange(key: String, start: String, end: String): List<StreamEntry> {
            val stream = entries[key] ?: return emptyList()

            val startId = parseRangeId(start, 0L)
            val endId = parseRangeId(end, Long.MAX_VALUE)

            return stream.values.filter { entry ->
                val entryId = StreamId.parse(entry.id)
                startId <= entryId && entryId <= endId
            }
        }

        private fun parseRangeId(id: String, defaultSequence: Long): StreamId {
            if (id == "-") {
                return StreamId(0L, 0L)
            }

            if (id == "+") {
                return StreamId(Long.MAX_VALUE, Long.MAX_VALUE)
            }

            if (!id.contains("-")) {
                return StreamId(id.toLong(), defaultSequence)
            }

            return StreamId.parse(id)
        }

        fun generateId(key: String, id: String): StreamIdGeneration {
            val lastId = entries[key]?.keys?.lastOrNull()?.let { StreamId.parse(it) }
            val sequenceTracker = sequenceTrackers.getOrPut(key) { mutableMapOf() }

            return StreamId.generate(id, lastId, sequenceTracker)
        }

        fun contains(key: String): Boolean = key in entries

        data class StreamEntry(
            val id: String,
            val keyPairs: List<Pair<String, String>>
        )

        data class StreamId(
            val time: Long,
            val sequenceNumber: Long
        ) : Comparable<StreamId> {

            override fun compareTo(other: StreamId): Int =
                compareValuesBy(this, other, { it.time }, { it.sequenceNumber })

            fun value(): String = "$time-$sequenceNumber"

            override fun toString(): String = value()

            companion object {
                fun parse(id: String): StreamId {
                    val parts = id.split("-")
                    return StreamId(parts[0].toLong(), parts[1].toLong())
                }

                fun generate(
                    input: String,
                    lastId: StreamId?,
                    sequenceTracker: MutableMap<Long, AtomicLong>
                ): StreamIdGeneration {
                    if (input == "0-0") {
                        return StreamIdGeneration.Error("ERR The ID specified in XADD must be greater than 0-0")
                    }

                    return when {
                        input == "*" -> generateFully(lastId, sequenceTracker)
                        input.endsWith("-*") -> generatePartially(input, lastId, sequenceTracker)
                        else -> generateExplicit(input, lastId, sequenceTracker)
                    }
                }

                private fun generateFully(
                    lastId: StreamId?,
                    sequenceTracker: MutableMap<Long, AtomicLong>
                ): StreamIdGeneration {
                    val now = System.currentTimeMillis()
                    val time = if (lastId != null && lastId.time >= now) lastId.time else now

                    val sequence = sequenceTracker.getOrPut(time) { AtomicLong(-1) }
                    sequence.incrementAndGet()

                    return StreamIdGeneration.Success(StreamId(time, sequence.toLong()))
                }

                private fun generatePartially(
                    input: String,
                    lastId: StreamId?,
                    sequenceTracker: MutableMap<Long, AtomicLong>
                ): StreamIdGeneration {
                    val time = input.substringBefore("-").toLong()

                    if (lastId != null && lastId.time > time) {
                        return StreamIdGeneration.Error(
                            "ERR The ID specified in XADD is equal or smaller than the target stream top item"
                        )
                    }

                    val sequence = if (time in sequenceTracker) {
                        sequenceTracker[time]!!.incrementAndGet()
                    } else {
                        val initial = if (time == 0L) 1L else 0L
                        sequenceTracker[time] = AtomicLong(initial)
                        initial
                    }

                    return StreamIdGeneration.Success(StreamId(time, sequence))
                }

                private fun generateExplicit(
                    input: String,
                    lastId: StreamId?,
                    sequenceTracker: MutableMap<Long, AtomicLong>
                ): StreamIdGeneration {
                    val parts = input.split("-")
                    if (parts.size != 2) {
                        return StreamIdGeneration.Error("Invalid ID specified in XADD")
                    }

                    val newId = StreamId(parts[0].toLong(), parts[1].toLong())

                    if (lastId != null && newId <= lastId) {
                        return StreamIdGeneration.Error(
                            "ERR The ID specified in XADD is equal or smaller than the target stream top item"
                        )
                    }

                    sequenceTracker[newId.time] = AtomicLong(newId.sequenceNumber)

                    return StreamIdGeneration.Success(newId)
                }
            }
        }

        sealed class StreamIdGeneration {
            data class Success(val id: StreamId) : StreamIdGeneration()
            data class Error(val message: String) : StreamIdGeneration()
        }
    }
}
