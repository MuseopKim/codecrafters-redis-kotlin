import java.net.ServerSocket
import java.util.stream.IntStream

fun main(args: Array<String>) {
    var serverSocket = ServerSocket(6379)
    // Since the tester restarts your program quite often, setting SO_REUSEADDR
    // ensures that we don't run into 'Address already in use' errors
    serverSocket.reuseAddress = true

    val clientSocket = serverSocket.accept()
    println("accepted new connection")

    try {
        while (true) {
            val reader = clientSocket.inputStream.bufferedReader()
            val writer = clientSocket.outputStream.bufferedWriter()

            val multipleCommands: MutableList<String> = mutableListOf();
            val commandCountRESP = reader.readLine() ?: return
            val commandCount = commandCountRESP.split("*")[1].toLong()
            println("Command count: $commandCount")
            (1..commandCount).forEach { _ ->
                val characterCount = reader.readLine().split("$")[1].toLong()
                val command = reader.readLine()
                println("$command $characterCount")
                multipleCommands.add(command)
            }


            (1..multipleCommands.size).forEach { _ -> writer.write("+PONG\r\n") }
            writer.flush()
        }
    } finally {
        clientSocket.close()
    }
}
