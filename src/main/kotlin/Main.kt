import java.net.ServerSocket

fun main(args: Array<String>) {
    var serverSocket = ServerSocket(6379)
    // Since the tester restarts your program quite often, setting SO_REUSEADDR
    // ensures that we don't run into 'Address already in use' errors
    serverSocket.reuseAddress = true

    val clientSocket = serverSocket.accept()
    println("accepted new connection")

    val reader = clientSocket.inputStream.bufferedReader()
    val writer = clientSocket.outputStream.bufferedWriter()

    while (!clientSocket.isClosed) {
        reader.forEachLine {
            if (it.startsWith("PING")) {
                writer.write("+PONG\r\n")
                writer.flush()
            }
        }
    }
}
