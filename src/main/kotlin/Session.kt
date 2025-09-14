import java.io.Reader
import java.io.Writer
import java.net.Socket

class Session(
    private val socket: Socket
) : Runnable {

    private val reader: Reader
    private val writer: Writer

    init {
        socket.soTimeout = 30_000
        reader = socket.inputStream.bufferedReader()
        writer = socket.outputStream.bufferedWriter()
    }

    override fun run() {
        socket.use { _ ->
            reader.forEachLine {
                if (it.startsWith("PING")) {
                    writer.write("+PONG\r\n")
                    writer.flush()
                }
            }
        }
    }
}
