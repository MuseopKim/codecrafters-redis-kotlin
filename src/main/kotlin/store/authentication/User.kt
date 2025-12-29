package store.authentication

import protocol.RedisValue
import java.security.MessageDigest

data class User(
    val username: String,
    val flags: MutableList<String> = mutableListOf(),
    val passwords: MutableList<String> = mutableListOf()
) {

    fun setProperty(property: String) {
        val firstCharacter = property.first()
        val normalized = property.drop(1)

        when (firstCharacter) {
            '>' -> addPassword(normalized)
        }
    }

    fun addPassword(password: String) {
        flags.remove("nopass")
        passwords.add(encode(password))
    }

    private fun encode(password: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(password.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun toRedisValue(): RedisValue.Array = RedisValue.Array(
        listOf(
            RedisValue.BulkString("flags"),
            RedisValue.Array(flags.map { RedisValue.BulkString(it) }),
            RedisValue.BulkString("passwords"),
            RedisValue.Array(passwords.map { RedisValue.BulkString(it) })
        )
    )
}
