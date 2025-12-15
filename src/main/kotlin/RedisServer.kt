import store.RedisDataStore
import java.net.ServerSocket

class RedisServer(
    private val host: String,
    private val port: Int,
    private val role: Role,
) {

    fun run() {
        val serverSocket = ServerSocket(port)
        // Since the tester restarts your program quite often, setting SO_REUSEADDR
        // ensures that we don't run into 'Address already in use' errors
        serverSocket.reuseAddress = true

        val serverMetadata = Metadata(role = role)

        val dataStore = RedisDataStore()
        while (true) {

            val clientSocket = serverSocket.accept()

            val thread = Thread(Session(serverMetadata, clientSocket, dataStore))
            thread.start()
        }
    }

    data class Metadata(
        private val version: String = "7.2.4",
        private val connectedClients: Int = 1,
        private val usedMemory: Int = 859944,
        private val role: Role = Role.MASTER,
        private val connectedSlaves: Int = 0,
        private val masterReplid: String = "8371b4fb1155b71f4a04d3e1bc3e18c4a990aeeb",
        private val masterReplOffset: Int = 0,
        private val secondReplOffset: Int = -1,
        private val replBacklogActive: Int = 0,
        private val replBacklogSize: Int = 1048576,
        private val replBacklogFirstByteOffset: Int = 0,
        private val replBacklogHistlen: Int? = null
    ) {
        fun information(): String {
            return """
                # Server
                redis_version:${version}
                # Clients
                connected_clients:${connectedClients}
                # Memory
                used_memory:${usedMemory}
                # Replication
                role:${role.alias}
            """.trimIndent()
        }

        fun replication(): String {
            return """
                role:${role.alias}
                connected_slaves:${connectedSlaves}
                master_replid:${masterReplid}
                master_repl_offset:${masterReplOffset}
                second_repl_offset:${secondReplOffset}
                repl_backlog_active:${replBacklogActive}
                repl_backlog_size:${replBacklogSize}
                repl_backlog_first_byte_offset:${replBacklogFirstByteOffset}
                repl_backlog_histlen:
            """.trimIndent()
        }
    }
}

enum class Role(
    val alias: String
) {
    MASTER("master"),
    REPLICA("slave");
}
