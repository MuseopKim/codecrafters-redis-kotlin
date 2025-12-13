package store.streams

import protocol.RedisValue
import store.KeyStore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

class Streams(
    private val sequenceTrackers: MutableMap<String, MutableMap<Long, AtomicLong>> = mutableMapOf(),
    private val entries: LinkedHashMap<String, LinkedHashMap<String, Entry>> = linkedMapOf(),
    private val lock: ReentrantLock = ReentrantLock(),
    private val condition: Condition = lock.newCondition()
) : KeyStore {

    fun xadd(key: String, id: String, keyPairs: List<Pair<String, String>>): String {
        lock.lock()
        try {
            val stream = entries.getOrPut(key) { linkedMapOf() }
            val entry = stream.getOrPut(id) { Entry(key, id, keyPairs) }
            condition.signalAll()

            return entry.id
        } finally {
            lock.unlock()
        }
    }

    fun xrange(key: String, start: String, end: String): List<Entry> {
        val stream = entries[key] ?: return emptyList()

        val startId = parseRangeId(start, 0L)
        val endId = parseRangeId(end, Long.MAX_VALUE)

        return stream.values.filter { entry ->
            val entryId = StreamId.parse(entry.id)
            startId <= entryId && entryId <= endId
        }
    }

    fun xread(query: StreamQuery): Map<String, List<Entry>>? {
        fun read(): Map<String, List<Entry>> {
            return query.keyIds()
                .flatMap { (key, id) ->
                    val stream = entries[key] ?: return@flatMap emptyList()
                    val startId = StreamId.parse(id)

                    stream.entries
                        .filter { startId < StreamId.parse(it.key) }
                        .map { it.value }
                }
                .groupBy { it.key }
        }

        if (!query.isBlocking()) {
            val result = read()
            return result.ifEmpty { null }
        }

        lock.lock()
        try {
            val timeoutNano = TimeUnit.MILLISECONDS.toNanos(query.blockTimeout!!)
            val waitUntil = System.nanoTime() + timeoutNano

            while (true) {
                val result = read()
                if (result.isNotEmpty()) {
                    return result
                }

                val remaining = waitUntil - System.nanoTime()
                if (query.blockTimeout == 0L) {
                    condition.await()
                    continue
                }

                if (remaining <= 0) {
                    return null
                }

                condition.awaitNanos(remaining)
            }
        } finally {
            lock.unlock()
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

    override operator fun contains(key: String): Boolean = key in entries

    data class Entry(
        val key: String,
        val id: String,
        val keyPairs: List<Pair<String, String>>
    ) {
        fun toRedisValue(): RedisValue.Array {
            val keyPairsAsBulkString = mutableListOf<RedisValue.BulkString>()

            for (keyPair in keyPairs) {
                val key = keyPair.first
                val value = keyPair.second
                keyPairsAsBulkString.add(RedisValue.BulkString(key))
                keyPairsAsBulkString.add(RedisValue.BulkString(value))
            }

            return RedisValue.Array(
                listOf(
                    RedisValue.BulkString(id),
                    RedisValue.Array(keyPairsAsBulkString)
                )
            )
        }
    }
}
