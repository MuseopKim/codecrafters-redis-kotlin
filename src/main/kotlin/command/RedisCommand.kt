package command

sealed class RedisCommand {

    abstract fun execute(arguments: List<RedisValue>, store: RedisDataStore): RedisValue

    object EchoCommand : RedisCommand() {

        override fun execute(arguments: List<RedisValue>, store: RedisDataStore): RedisValue {
            val argument = arguments.first()

            if (argument is RedisValue.BulkString) {
                return RedisValue.BulkString(argument.value)
            }

            return RedisValue.Error("Invalid arguments.")
        }
    }

    object PingCommand : RedisCommand() {

        override fun execute(
            arguments: List<RedisValue>,
            store: RedisDataStore
        ): RedisValue {
            return RedisValue.SimpleString("PONG")
        }
    }

    object GetCommand : RedisCommand() {

        override fun execute(arguments: List<RedisValue>, store: RedisDataStore): RedisValue {
            val key = arguments.first()
            if (key !is RedisValue.BulkString) {
                return RedisValue.Error("Invalid arguments.")
            }

            return store.get(key.value)?.let { RedisValue.BulkString(it) }
                ?: RedisValue.NullBulkString
        }
    }

    object SetCommand : RedisCommand() {

        override fun execute(
            arguments: List<RedisValue>,
            store: RedisDataStore
        ): RedisValue {
            if (arguments.size < 2) {
                return RedisValue.Error("Invalid arguments.")
            }

            val key = arguments.first()
            val value = arguments[1]

            if (key !is RedisValue.BulkString || value !is RedisValue.BulkString) {
                return RedisValue.Error("Invalid arguments.")
            }

            if (arguments.size == 2) {
                store.set(key.value, value.value)
                return RedisValue.SimpleString("OK")
            }

            if (arguments.size < 4) {
                return RedisValue.Error("Invalid arguments.")
            }

            val expiry = arguments[2]
            if (expiry !is RedisValue.BulkString) {
                return RedisValue.Error("Invalid arguments.")
            }

            val seconds = arguments[3]
            if (seconds !is RedisValue.BulkString) {
                return RedisValue.Error("Invalid arguments.")
            }

            val px = when (expiry.value.uppercase()) {
                "PX" -> seconds.value.toLong()
                "EX" -> seconds.value.toLong() * 1000
                else -> null
            }

            px ?: return RedisValue.Error("Invalid arguments.")

            store.set(key.value, value.value, px)

            return RedisValue.SimpleString("OK")
        }
    }
}

