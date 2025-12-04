package command;

class CommandDispatcher {

    fun dispatch(value: RedisValue, store: RedisDataStore): RedisValue {
        if (value !is RedisValue.Array) {
            throw IllegalArgumentException("The value is not a command.")
        }

        val array = value.elements
        val commandName = array.first() as RedisValue.BulkString
        val arguments = array.drop(1)

        val redisCommand = when (commandName.value.uppercase()) {
            "ECHO" -> RedisCommand.EchoCommand
            "PING" -> RedisCommand.PingCommand
            "GET" -> RedisCommand.GetCommand
            "SET" -> RedisCommand.SetCommand
            "RPUSH" -> RedisCommand.RPUSHCommand
            "LRANGE" -> RedisCommand.LRANGECommand
            else -> throw IllegalArgumentException("Unknown command")
        }

        return redisCommand.execute(arguments, store)
    }
}
