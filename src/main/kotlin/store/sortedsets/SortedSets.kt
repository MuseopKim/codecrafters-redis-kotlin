package store.sortedsets

import store.KeyStore
import java.util.concurrent.ConcurrentSkipListMap

class SortedSets(
    private val entries: MutableMap<String, SortedSet> = mutableMapOf()
) : KeyStore {

    fun zadd(setName: String, score: Double, memberName: String): Long {
        val sortedSet = entries.getOrPut(setName) { SortedSet() }
        val count = sortedSet.add(memberName, score)

        return count
    }

    override fun contains(key: String): Boolean = key in entries
}

class SortedSet(
    private val scores: MutableMap<String, Double> = mutableMapOf(),
    private val members: ConcurrentSkipListMap<Double, MutableSet<Entry>> = ConcurrentSkipListMap()
) {

    fun add(key: String, score: Double): Long {
        val existScore = scores[key]
        val exist = existScore != null
        if (exist) {
            removeEntry(key, existScore)
        }

        addEntry(key, score)

        return if (exist) {
            0
        } else {
            1
        }
    }

    private fun removeEntry(key: String, score: Double) {
        scores.remove(key)
        members[score]?.removeIf { entry -> entry.key == key }
    }

    private fun addEntry(key: String, score: Double) {
        scores[key] = score
        members.getOrPut(score) { mutableSetOf() }.add(Entry(score, key))
    }

    fun size(): Long = scores.size.toLong()

    data class Entry(
        val score: Double,
        val key: String
    )
}
