import java.io.InputStream
import java.io.Reader
import java.io.Writer
import java.net.ServerSocket
import java.net.Socket

fun main(args: Array<String>) {
    var serverSocket = ServerSocket(6379)
    // Since the tester restarts your program quite often, setting SO_REUSEADDR
    // ensures that we don't run into 'Address already in use' errors
    serverSocket.reuseAddress = true

    while (true) {
        val clientSocket = serverSocket.accept()

        Thread({ handleSession(clientSocket) }).start()
    }
}

fun handleSession(
    socket: Socket,
) {
    val reader = socket.inputStream.bufferedReader()
    val writer = socket.outputStream.bufferedWriter()

    while (!socket.isClosed) {
        reader.forEachLine {
            if (it.startsWith("PING")) {
                writer.write("+PONG\r\n")
                writer.flush()
            }
        }
    }
}
