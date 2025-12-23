package store.pubsub

import Session
import java.util.concurrent.ConcurrentHashMap

class PubSub(
    private val entries: ConcurrentHashMap<String, Channel> = ConcurrentHashMap()
) {

    fun subscribe(channelName: String, session: Session) {
        val channel = entries.computeIfAbsent(channelName) { Channel(channelName) }
        channel.addSubscriber(session)
    }
}
