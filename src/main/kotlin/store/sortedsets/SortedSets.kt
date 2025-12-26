package store.sortedsets

import store.KeyStore
import java.util.*
import java.util.concurrent.ConcurrentSkipListMap
import kotlin.math.max

class SortedSets(
    private val entries: MutableMap<String, SortedSet> = mutableMapOf()
) : KeyStore {

    fun zadd(setName: String, score: Double, memberName: String): Long {
        val sortedSet = entries.getOrPut(setName) { SortedSet() }
        val count = sortedSet.add(memberName, score)

        return count
    }

    fun zrank(setName: String, memberName: String): Long? {
        return entries[setName]?.rank(memberName)
    }

    fun zrange(setName: String, start: Long, end: Long): List<SortedSet.Entry> {
        val sortedSet = entries[setName] ?: return emptyList()
        return sortedSet.range(start, end)
    }

    fun zcard(setName: String): Long {
        return entries[setName]?.size() ?: 0L
    }

    override fun contains(key: String): Boolean = key in entries
}

class SortedSet(
    private val scores: MutableMap<String, Double> = mutableMapOf(),
    private val members: ConcurrentSkipListMap<Double, TreeMap<String, Entry>> = ConcurrentSkipListMap()
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

    fun rank(key: String): Long? {
        if (key !in scores) {
            return null
        }

        var rank = 0L
        for ((score, scoreEntries) in members.entries) {
            if (scores[key] == score) {
                break
            }

            rank += scoreEntries.size.toLong()
        }

        rank += members[scores[key]]!!.values.indexOfFirst { it.key == key }

        return rank
    }

    fun range(start: Long, end: Long): List<Entry> {
        fun normalizeIndex(index: Long, length: Long): Long {
            if (index >= 0) {
                return index
            }

            val normalizedIndex = length + index

            return max(0, normalizedIndex)
        }

        val normalizedStart = normalizeIndex(start, scores.size.toLong())
        val normalizedEnd = normalizeIndex(end, scores.size.toLong())

        val entriesInRange = mutableListOf<Entry>()

        var index = 0L
        for ((_, entries) in members.entries) {
            for ((_, entry) in entries) {
                if (normalizedStart <= index && index <= normalizedEnd) {
                    entriesInRange.add(entry)
                }

                index++
                if (index > normalizedEnd) {
                    break
                }
            }
        }

        return entriesInRange
    }

    private fun removeEntry(key: String, score: Double) {
        scores.remove(key)
        members[score]?.remove(key)
    }

    private fun addEntry(key: String, score: Double) {
        scores[key] = score
        members.getOrPut(score) { TreeMap() }.put(key, Entry(score, key))
    }

    fun size(): Long = scores.size.toLong()

    data class Entry(
        val score: Double,
        val key: String
    )
}
