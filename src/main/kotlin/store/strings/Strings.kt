package store.strings

import store.KeyStore

class Strings(
    private val entries: MutableMap<String, Entry> = mutableMapOf()
) : KeyStore {

    fun set(key: String, value: String, px: Long? = null) {
        val expiresAt = px?.let { System.currentTimeMillis() + it }
        entries[key] = Entry(value, expiresAt)
    }

    fun get(key: String): String? {
        val currentTime = System.currentTimeMillis()
        val entry = entries[key] ?: return null

        if (entry.isExpired(currentTime)) {
            entries.remove(key)
            return null
        }

        return entry.value
    }

    override operator fun contains(key: String): Boolean = key in entries

    data class Entry(
        val value: String,
        val expiresAt: Long? = null
    ) {
        fun isExpired(currentTimestamp: Long): Boolean =
            expiresAt != null && expiresAt <= currentTimestamp
    }
}
