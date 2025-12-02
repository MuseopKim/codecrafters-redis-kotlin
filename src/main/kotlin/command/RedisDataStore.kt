package command

class RedisDataStore {

    private val strings = mutableMapOf<String, Entry>()

    fun set(key: String, value: String, px: Long? = null) {
        val expiresAt = px?.let { System.currentTimeMillis() + it }

        strings[key] = Entry(value, expiresAt)
    }

    fun get(key: String): String? {
        val currentTime = System.currentTimeMillis()

        val entry = strings[key] ?: return null

        if (entry.expiresAt?.let { it < currentTime} == true) {
            strings.remove(key)
            return null
        }

        return entry.value
    }

    data class Entry(
        val value: String,
        val expiresAt: Long? = null
    )
}

