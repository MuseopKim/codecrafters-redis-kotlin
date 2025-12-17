import protocol.RedisValue
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.Socket

data class Connection(
    private val socket: Socket,
    private val reader: BufferedReader,
    private val writer: BufferedWriter
) {
    fun read() {
        reader.readLine()
    }

    fun ping() {
        val ping = RedisValue.Array(listOf(RedisValue.BulkString("PING")))
        writer.write(ping.encodeValue())
        writer.flush()
    }

    fun replconf(arguments: List<String>) {
        val argumentsAsBulkString = mutableListOf(RedisValue.BulkString("REPLCONF"))

        argumentsAsBulkString.addAll(arguments.map { RedisValue.BulkString(it) })

        writer.write(RedisValue.Array(argumentsAsBulkString).encodeValue())
        writer.flush()
    }

    fun psync(replicationId: String, offset: Long) {
        val argumentAsBulkString = mutableListOf(
            RedisValue.BulkString("PSYNC"),
            RedisValue.BulkString(replicationId),
            RedisValue.BulkString(offset.toString())
        )

        writer.write(RedisValue.Array(argumentAsBulkString).encodeValue())
        writer.flush()
    }
}
