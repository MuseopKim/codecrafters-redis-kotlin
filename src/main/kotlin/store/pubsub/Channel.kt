package store.pubsub

import Session
import protocol.RedisValue
import java.util.concurrent.CopyOnWriteArrayList

class Channel(
    val name: String,
    private val subscribers: CopyOnWriteArrayList<Session> = CopyOnWriteArrayList<Session>()
) {

    fun addSubscriber(session: Session) {
        this.subscribers.add(session)
        session.addSubscribedChannel(name)
    }

    fun publish(message: String): Long {
        this.subscribers.forEach { it.send(RedisValue.BulkString(message)) }
        return subscribers.size.toLong()
    }
}
