package command

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

    fun rpush(key: String, value: String): Int {
        val list = lists.getOrPut(key) { mutableListOf() }
        list.add(value)

        return list.size
    }

    data class Entry(
        val value: String,
        val expiresAt: Long? = null
    ) {
        fun isExpired(currentTimestamp: Long): Boolean =
            expiresAt != null && expiresAt <= currentTimestamp
    }
}

