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
        private val idTimeIncrementer: MutableMap<String, AtomicLong> = mutableMapOf(),
        private val ids: LinkedHashMap<String, LinkedHashMap<Long, AtomicLong>> = linkedMapOf(),
        private val entries: LinkedHashMap<String, LinkedHashMap<String, StreamEntry>> = linkedMapOf()

    ) {

        fun xadd(key: String, id: String, keyPairs: List<Pair<String, String>>): String {
            val stream = entries.getOrPut(key) { linkedMapOf() }
            val entry = stream.getOrPut(id) { StreamEntry(id, keyPairs) }

            return entry.id
        }

        fun xrange(key: String, start: String, end: String): List<StreamEntry> {
            if (key !in entries) {
                return emptyList()
            }

            val idsInRange = idsInRange(key, start, end)
            if (idsInRange.isEmpty()) {
                return emptyList()
            }

            val stream = entries[key]!!

            return idsInRange.map { stream[it]!! }
        }

        private fun idsInRange(key: String, start: String, end: String): List<String> {
            val idsInRange = mutableListOf<String>()
            if (key !in entries) {
                return idsInRange
            }

            val startId = parseId(key, start, 0L)
            val endId = parseId(key, end, Long.MAX_VALUE)

            val streamIds = ids[key]!!
            val fromEntries = streamIds.entries
                .dropWhile { it.key != startId.time }
                .associate { it.key to it.value }

            for (entry in fromEntries) {
                val time = entry.key
                val sequence = entry.value
                if (time > endId.time) {
                    break
                }

                if (time == endId.time && sequence.toLong() > endId.sequenceNumber) {
                    break
                }

                val startSequence = when (time) {
                    startId.time -> startId.sequenceNumber
                    0L -> 1L
                    else -> 0L
                }

                val endSequence = if (time == endId.time) {
                    min(sequence.toLong(), endId.sequenceNumber)
                } else {
                    sequence.toLong()
                }

                for (i in startSequence..endSequence) {
                    idsInRange.add("${time}-${i}")

                }
            }

            return idsInRange
        }

        private fun parseId(key: String, id: String, defaultSequence: Long): StreamId {
            if (!id.contains("-")) {
                val streamIds = ids[key]!!
                val maxSequence = streamIds[id.toLong()]!!
                return StreamId(id.toLong(), min(maxSequence.toLong(), defaultSequence))
            }

            val parts = id.split("-")
            val time = parts[0].toLong()
            val sequence = parts[1].toLong()

            return StreamId(time, sequence)
        }

        fun getEntry(key: String, id: String): StreamEntry? {
            return entries[key]?.let { stream -> stream[id] }
        }

        fun getLastEntry(key: String): StreamEntry? {
            return entries[key]?.lastEntry()?.value
        }

        fun generateId(key: String, id: String): StreamIdGeneration {
            if (id == "0-0") {
                return StreamIdGeneration.Error("ERR The ID specified in XADD must be greater than 0-0")
            }

            idTimeIncrementer.getOrPut(key) {
                AtomicLong(0)
            }

            ids.getOrPut(key) {
                linkedMapOf(
                    0L to AtomicLong(0)
                )
            }

            if (id == "*") {
                return StreamIdGeneration.Success(fullyAutoGeneratedId(key, id))
            }

            if (id.endsWith("-*")) {
                val errorMessage = validatePartiallyAutoGeneratedId(key, id)

                return errorMessage?.let { StreamIdGeneration.Error(it) }
                    ?: StreamIdGeneration.Success(partiallyAutoGeneratedId(key, id))
            }

            val errorMessage = validateExplicitId(key, id)

            return errorMessage?.let { StreamIdGeneration.Error(it) }
                ?: StreamIdGeneration.Success(explicitId(key, id))
        }

        private fun fullyAutoGeneratedId(key: String, id: String): StreamId {
            val streamIds = ids.get(key)!!
            val lastIdTime = idTimeIncrementer.get(key)!!

            lastIdTime.set(System.currentTimeMillis())
            val sequenceNumber = streamIds.getOrPut(lastIdTime.toLong()) {
                AtomicLong(-1)
            }

            sequenceNumber.incrementAndGet()

            streamIds.put(lastIdTime.toLong(), sequenceNumber)

            return StreamId(lastIdTime.toLong(), sequenceNumber.toLong())
        }

        private fun partiallyAutoGeneratedId(key: String, id: String): StreamId {
            val streamIds = ids[key]!!

            val parts = id.split("-")
            val newIdTime = parts.first().toLong()

            if (newIdTime !in streamIds) {
                val initialSequence = AtomicLong(0)
                streamIds.put(newIdTime, initialSequence)
                return StreamId(newIdTime, initialSequence.toLong())
            }

            val lastSequence = streamIds.get(newIdTime)!!
            return StreamId(newIdTime, lastSequence.incrementAndGet())
        }

        private fun validatePartiallyAutoGeneratedId(key: String, id: String): String? {
            val parts = id.split("-")
            if (parts.size != 2) {
                return "Invalid ID specified in XADD"
            }

            val time = parts.first().toLong()

            val lastId = idTimeIncrementer.get(key)!!
            if (lastId.toLong() > time) {
                return "ERR The ID specified in XADD is equal or smaller than the target stream top item"
            }

            return null
        }

        private fun explicitId(key: String, id: String): StreamId {
            val parts = id.split("-")

            val time = parts.first()
            val sequenceNumber = parts.last()

            val streamIds = ids[key]!!
            streamIds[time.toLong()] = AtomicLong(sequenceNumber.toLong())

            val incrementer = idTimeIncrementer.get(key)!!
            incrementer.set(time.toLong())

            return StreamId(time.toLong(), sequenceNumber.toLong())
        }

        private fun validateExplicitId(key: String, id: String): String? {
            val parts = id.split("-")
            if (parts.size != 2) {
                return "Invalid ID specified in XADD"
            }

            val newTime = parts.first()
            val newSequence = parts.last()

            val streamIds = ids.get(key)!!
            val lastIdTime = idTimeIncrementer.get(key)!!
            val lastSequence = streamIds.get(lastIdTime.toLong())?.toLong() ?: -1

            if (lastIdTime.toLong() > newTime.toLong()) {
                return "ERR The ID specified in XADD is equal or smaller than the target stream top item"
            }

            if (lastIdTime.toLong() == newTime.toLong() && lastSequence >= newSequence.toLong()) {
                return "ERR The ID specified in XADD is equal or smaller than the target stream top item"
            }

            return null
        }

        fun contains(key: String): Boolean = key in entries

        data class StreamEntry(
            val id: String,
            val keyPairs: List<Pair<String, String>>
        )

        data class StreamId(
            val time: Long,
            val sequenceNumber: Long
        ) {
            fun value(): String = "$time-$sequenceNumber"
        }

        sealed class StreamIdGeneration {
            data class Success(val id: StreamId) : StreamIdGeneration()
            data class Error(val message: String) : StreamIdGeneration()
        }
    }
}
