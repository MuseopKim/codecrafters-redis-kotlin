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
}

