package store.authentication

import protocol.RedisValue
import java.security.MessageDigest

data class User(
    val username: String,
    val flags: MutableList<String> = mutableListOf(),
    val passwords: MutableList<String> = mutableListOf()
) {

    fun setProperty(property: String): User {
        val firstCharacter = property.first()
        val normalized = property.drop(1)

        return when (firstCharacter) {
            '>' -> addPassword(normalized)
            else -> this
        }
    }

    fun addPassword(password: String): User {
        val user = this.deepCopy()
        user.flags.remove("nopass")
        user.passwords.add(encode(password))
        return user
    }

    fun hasPassword(password: String): Boolean {
        val encodedPassword = encode(password)
        return passwords.any { encodedPassword == it }
    }

    private fun encode(password: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(password.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun deepCopy(): User = copy(
        flags = flags.toMutableList(),
        passwords = passwords.toMutableList()
    )

    fun toRedisValue(): RedisValue.Array = RedisValue.Array(
        listOf(
            RedisValue.BulkString("flags"),
            RedisValue.Array(flags.map { RedisValue.BulkString(it) }),
            RedisValue.BulkString("passwords"),
            RedisValue.Array(passwords.map { RedisValue.BulkString(it) })
        )
    )
}
