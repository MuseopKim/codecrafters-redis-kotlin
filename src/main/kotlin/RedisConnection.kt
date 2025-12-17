import command.RedisCommandParser
import protocol.RedisValue
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.net.Socket

class RedisConnection(
    private val socket: Socket,
) : Closeable {

    private val reader: BufferedReader = socket.inputStream.bufferedReader()
    private val writer: BufferedWriter = socket.outputStream.bufferedWriter()
    private val respParser: RedisCommandParser = RedisCommandParser(reader)

    fun send(value: RedisValue) {
        writer.write(value.encodeValue())
        writer.flush()
    }

    fun receive(): Result<RedisValue> = runCatching { respParser.parse() }

    override fun close() {
        runCatching { socket.close() }
        runCatching { reader.close() }
        runCatching { writer.close() }
    }

    companion object {
        fun connect(host: String, port: Int, timeout: Int = 3000): RedisConnection {
            val socket = Socket(host, port).apply { soTimeout = timeout }

            return RedisConnection(socket)
        }
    }
}
