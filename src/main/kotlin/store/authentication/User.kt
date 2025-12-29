package store.authentication

import protocol.RedisValue

data class User(
    val username: String,
    val flags: MutableList<String> = mutableListOf(),
    val passwords: MutableList<String> = mutableListOf()
) {

    fun toRedisValue(): RedisValue.Array = RedisValue.Array(
        listOf(
            RedisValue.BulkString("flags"),
            RedisValue.Array(flags.map { RedisValue.BulkString(it) }),
            RedisValue.BulkString("passwords"),
            RedisValue.Array(passwords.map { RedisValue.BulkString(it) })
        )
    )
}
