import command.CommandDispatcher
import replication.Replication
import store.RedisDataStore

class SessionHandler(
    private val session: Session,
    private val store: RedisDataStore,
    private val replication: Replication
) : Runnable {

    private val commandDispatcher: CommandDispatcher = CommandDispatcher()

    override fun run() {
        session.useSocket {
            while (true) {
                val command = session.readCommand()
                val commandExecution = commandDispatcher.dispatch(session, command, store)
                val executedCommandResponse = commandExecution.result

                session.write(executedCommandResponse)

                replication.handlePostExecution(commandExecution, session)
            }
        }
    }
}
