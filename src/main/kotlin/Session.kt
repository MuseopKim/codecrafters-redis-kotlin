import command.RedisCommand
import command.RedisCommandParser
import protocol.RedisValue
import store.RedisDataStore
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.Socket

class Session(
    private val serverMetadata: RedisServer.Metadata,
    private val socket: Socket,
    private val dataStore: RedisDataStore
) {

    var type: Type = Type.CLIENT

    private val reader: BufferedReader
    private val writer: BufferedWriter
    private val commands: MutableList<RedisCommand.Entry>
    private var transaction: Boolean = false
    private val commandParser: RedisCommandParser

    init {
        socket.soTimeout = 30_000
        reader = socket.inputStream.bufferedReader()
        writer = socket.outputStream.bufferedWriter()
        commands = mutableListOf()
        commandParser = RedisCommandParser(reader)
    }

    fun readCommand(): RedisValue = commandParser.parse()

    fun <T> useSocket(callback: () -> T): T = socket.use { callback() }

    fun serverMetadata(): RedisServer.Metadata = serverMetadata

    fun isTransaction(): Boolean = transaction

    fun addCommand(command: RedisCommand, arguments: List<RedisValue>): Int {
        this.commands.add(RedisCommand.Entry(command, arguments))
        return commands.size
    }

    fun commands(): List<RedisCommand.Entry> = commands

    fun clearCommands() = commands.clear()

    fun transaction(transaction: Boolean): Boolean {
        this.transaction = transaction
        return transaction
    }

    fun write(value: RedisValue) {
        if (type == Type.REPLICA) {
            return
        }

        writer.write(value.encodeValue())
        writer.flush()
    }

    fun propagate(value: RedisValue) {
        writer.write(value.encodeValue())
        writer.flush()
    }

    fun write(bytes: ByteArray) {
        writer.flush()
        socket.outputStream.write(bytes)
        socket.outputStream.flush()
    }

    enum class Type {
        CLIENT,
        REPLICA
    }
}

