package store.pubsub

import Session
import java.util.concurrent.CopyOnWriteArrayList

class Channel(
    val name: String,
    private val subscribers: CopyOnWriteArrayList<Session> = CopyOnWriteArrayList<Session>()
) {

    fun addSubscriber(session: Session) {
        this.subscribers.add(session)
        session.addSubscribedChannel(name)
    }
}
