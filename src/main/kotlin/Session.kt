import command.CommandDispatcher
import command.RedisCommandParser
import command.RedisDataStore
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.Socket

class Session(
    private val socket: Socket,
    private val dataStore: RedisDataStore
) : Runnable {

    private val reader: BufferedReader
    private val writer: BufferedWriter

    init {
        socket.soTimeout = 30_000
        reader = socket.inputStream.bufferedReader()
        writer = socket.outputStream.bufferedWriter()
    }

    override fun run() {
        socket.use { _ ->
            val commandParser = RedisCommandParser(reader)
            val commandDispatcher = CommandDispatcher()

            while (true) {
                val parsed = commandParser.parse()
                val result = commandDispatcher.dispatch(parsed, dataStore)
                writer.write(result.encodeValue())
                writer.flush()
            }
        }
    }
}

