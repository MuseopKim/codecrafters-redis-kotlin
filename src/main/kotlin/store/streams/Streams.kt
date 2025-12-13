package store.streams

import java.util.concurrent.atomic.AtomicLong

class Streams(
    private val sequenceTrackers: MutableMap<String, MutableMap<Long, AtomicLong>> = mutableMapOf(),
    private val entries: LinkedHashMap<String, LinkedHashMap<String, Entry>> = linkedMapOf()
) {

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

    operator fun contains(key: String): Boolean = key in entries

    data class Entry(
        val id: String,
        val keyPairs: List<Pair<String, String>>
    )
}
