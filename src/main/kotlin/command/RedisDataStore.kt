package command

class RedisDataStore{

    private val strings = mutableMapOf<String, String>()

    fun set(key: String, value: String) {
        strings[key] = value
    }

    fun get(key: String): String? {
        return strings[key]
    }
}
