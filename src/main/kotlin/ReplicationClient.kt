import java.net.Socket

class ReplicationClient(
    private val masterHost: String,
    private val masterPort: Int
) {
    fun handshake() {
        val connection = connect()
        connection.ping()
    }

    private fun connect(): Connection {
        val socket = Socket(masterHost, masterPort)

        return Connection(
            socket,
            socket.inputStream.bufferedReader(),
            socket.outputStream.bufferedWriter()
        )
    }
}
