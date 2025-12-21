package replication

import Session
import command.CommandExecution
import command.RedisCommand
import java.util.concurrent.CopyOnWriteArrayList

sealed interface Replication {

    fun handlePostExecution(commandExecution: CommandExecution, session: Session)

    fun replicate(commandExecution: CommandExecution)

    fun registerReplica(session: Session)

    fun replicaCount(): Int

    class Master : Replication {

        private val replicas = CopyOnWriteArrayList<Session>()

        override fun handlePostExecution(
            commandExecution: CommandExecution,
            session: Session
        ) {
            if (commandExecution.command == RedisCommand.PSYNC) {
                registerReplica(session)
            }

            replicate(commandExecution)
        }

        override fun replicate(commandExecution: CommandExecution) {
            if (!commandExecution.isReplicable) {
                return
            }

            replicas.forEach { it ->
                try {
                    it.propagate(commandExecution.rawCommand)
                } catch (e: Exception) {
                    replicas.remove(it)
                }
            }
        }

        override fun registerReplica(session: Session) {
            session.type = Session.Type.REPLICA
            replicas.add(session)
        }

        override fun replicaCount(): Int = replicas.size
    }

    object Disabled : Replication {

        override fun handlePostExecution(commandExecution: CommandExecution, session: Session) =
            Unit

        override fun replicate(commandExecution: CommandExecution) = Unit

        override fun registerReplica(session: Session) = Unit

        override fun replicaCount(): Int = 0
    }
}
