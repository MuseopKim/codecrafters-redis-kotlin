import protocol.RedisValue
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.Socket

data class Connection(
    private val socket: Socket,
    private val reader: BufferedReader,
    private val writer: BufferedWriter
) {
    fun ping() {
        val ping = RedisValue.Array(listOf(RedisValue.BulkString("PING")))
        writer.write(ping.encodeValue())
        writer.flush()
    }
}
