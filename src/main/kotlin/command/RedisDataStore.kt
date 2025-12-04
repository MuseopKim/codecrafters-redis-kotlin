package command

import kotlin.math.min

class RedisDataStore {

    private val strings = mutableMapOf<String, Entry>()
    private val lists = mutableMapOf<String, MutableList<String>>()

    fun set(key: String, value: String, px: Long? = null) {
        val expiresAt = px?.let { System.currentTimeMillis() + it }

        strings[key] = Entry(value, expiresAt)
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

    fun rpush(key: String, values: List<String>): Int {
        val list = lists.getOrPut(key) { mutableListOf() }
        list.addAll(values)

        return list.size
    }

    fun lrange(key: String, start: Int, stop: Int): List<String> {
        val list = lists[key] ?: return emptyList()
        val length = list.size

        if (start > stop) {
            return emptyList()
        }

        if (start >= length) {
            return emptyList()
        }

        return list.subList(start, min(stop, length - 1) + 1)
    }

    data class Entry(
        val value: String,
        val expiresAt: Long? = null
    ) {
        fun isExpired(currentTimestamp: Long): Boolean =
            expiresAt != null && expiresAt <= currentTimestamp
    }
}

