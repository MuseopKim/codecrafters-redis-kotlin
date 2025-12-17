class Replica private constructor(
    private val port: Int,
    private val masterHost: String,
    private val masterPort: Int
) {

    companion object {
        fun connect(port: Int, masterHost: String, masterPort: Int): Result<Replica> = runCatching {
            val connection = RedisConnection.connect(masterHost, masterPort)

            try {
                val client = RedisClient(connection)

                client.ping().getOrThrow()
                client.replconf("listening-port", port.toString()).getOrThrow()
                client.replconf("capa", "psync2").getOrThrow()
                client.psync("?", -1)

                return Result.success(Replica(port, masterHost, masterPort))
            } catch (e: Exception) {
                connection.close()
                throw e
            }
        }
    }
}
