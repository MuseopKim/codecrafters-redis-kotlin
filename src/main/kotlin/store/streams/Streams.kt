package store.streams

import protocol.RedisValue
import store.KeyStore
import java.util.concurrent.atomic.AtomicLong

class Streams(
    private val sequenceTrackers: MutableMap<String, MutableMap<Long, AtomicLong>> = mutableMapOf(),
    private val entries: LinkedHashMap<String, LinkedHashMap<String, Entry>> = linkedMapOf()
) : KeyStore {

    fun xadd(key: String, id: String, keyPairs: List<Pair<String, String>>): String {
        val stream = entries.getOrPut(key) { linkedMapOf() }
        val entry = stream.getOrPut(id) { Entry(id, keyPairs) }

        return entry.id
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

    fun xread(key: String, id: String): List<Entry> {
        val stream = entries[key] ?: return emptyList()
        val startId = StreamId.parse(id)

        return stream.entries
            .filter { startId < StreamId.parse(it.key) }
            .map { it.value }
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
