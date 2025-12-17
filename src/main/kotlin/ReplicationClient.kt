import java.net.Socket

class ReplicationClient(
    private val host: String,
    private val port: Int,
    private val masterHost: String,
    private val masterPort: Int
) {
    fun handshake() {
        val connection = connect()
        connection.ping()
        connection.read()
        connection.replconf(listOf("listening-port", port.toString()))
        connection.read()
        connection.replconf(listOf("capa", "psync2"))
        connection.read()
        connection.psync("?", -1)
        connection.read()
    }

    private fun connect(): Connection {
        val socket = Socket(masterHost, masterPort)
        socket.soTimeout = 3000

        return Connection(
            socket,
            socket.inputStream.bufferedReader(),
            socket.outputStream.bufferedWriter()
        )
    }
}
