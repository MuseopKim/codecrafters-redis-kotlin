package command

sealed class RedisCommand {

    abstract fun execute(arguments: List<RedisValue>, store: RedisDataStore): RedisValue

    object EchoCommand : RedisCommand() {

        override fun execute(arguments: List<RedisValue>, store: RedisDataStore): RedisValue {
            val argument = arguments.getBulkString(0)
                ?: return RedisValue.Error("Invalid arguments.")

            return argument
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
            val key = arguments.getBulkString(0)
                ?: return RedisValue.Error("Invalid arguments.")

            return store.get(key.value)?.let { RedisValue.BulkString(it) }
                ?: RedisValue.NullBulkString
        }
    }

    object SetCommand : RedisCommand() {

        override fun execute(
            arguments: List<RedisValue>,
            store: RedisDataStore
        ): RedisValue {

            val key = arguments.getBulkString(0)
                ?: return RedisValue.Error("Invalid arguments.")

            val value = arguments.getBulkString(1)
                ?: return RedisValue.Error("Invalid arguments.")

            if (arguments.size == 2) {
                store.set(key.value, value.value)
                return RedisValue.SimpleString("OK")
            }

            val expiry = arguments.getBulkString(2)
                ?: return RedisValue.Error("Invalid arguments.")

            val timestamp = arguments.getBulkString(3)
                ?: return RedisValue.Error("Invalid arguments.")

            val px = when (expiry.value.uppercase()) {
                "PX" -> timestamp.value.toLong()
                "EX" -> timestamp.value.toLong() * 1000
                else -> null
            }

            px ?: return RedisValue.Error("Invalid arguments.")

            store.set(key.value, value.value, px)

            return RedisValue.SimpleString("OK")
        }
    }

    object RPUSHCommand : RedisCommand() {
        override fun execute(
            arguments: List<RedisValue>,
            store: RedisDataStore
        ): RedisValue {
            val key = arguments.getBulkString(0)
                ?: return RedisValue.Error("Invalid arguments.")

            val elements = arguments.getBulkStrings(1)
            if (elements.isEmpty()) {
                return RedisValue.Error("Invalid arguments.")
            }

            val sizeOfList = store.rpush(key.value, elements.map { it.value })

            return RedisValue.Integer(sizeOfList)
        }
    }

    object LPUSHCommand : RedisCommand() {
        override fun execute(
            arguments: List<RedisValue>,
            store: RedisDataStore
        ): RedisValue {
            val key = arguments.getBulkString(0)
                ?: return RedisValue.Error("Invalid arguments.")

            val elements = arguments.getBulkStrings(1)
            if (elements.isEmpty()) {
                return RedisValue.Error("Invalid arguments.")
            }

            val sizeOfList = store.lpush(key.value, elements.map { it.value })

            return RedisValue.Integer(sizeOfList)
        }
    }

    object LRANGECommand : RedisCommand() {
        override fun execute(
            arguments: List<RedisValue>,
            store: RedisDataStore
        ): RedisValue {
            val key = arguments.getBulkString(0)
                ?: return RedisValue.Error("Invalid arguments.")

            val start = arguments.getBulkString(1)
                ?: return RedisValue.Error("Invalid arguments.")

            val end = arguments.getBulkString(2)
                ?: return RedisValue.Error("Invalid arguments.")

            val subList = store.lrange(key.value, start.value.toInt(), end.value.toInt())

            return RedisValue.Array(subList.map { RedisValue.BulkString(it) })
        }
    }
}

