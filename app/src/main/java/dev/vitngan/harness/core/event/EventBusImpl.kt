package dev.vitngan.harness.core.event

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/** In-memory [EventBus] with copy-on-write subscriber lists. */
class EventBusImpl : EventBus {

    private data class Entry(
        val id: Long,
        val filter: EventFilter,
        val subscriber: (EventEnvelope) -> Unit,
    )

    private val entries = CopyOnWriteArrayList<Entry>()
    private val ids = AtomicLong(0)
    private val failures = ConcurrentHashMap.newKeySet<Long>()

    override fun subscribe(filter: EventFilter, subscriber: (EventEnvelope) -> Unit): Subscription {
        val entry = Entry(ids.incrementAndGet(), filter, subscriber)
        entries += entry
        return object : Subscription {
            override fun unsubscribe() {
                entries.remove(entry)
            }

            override val isActive: Boolean get() = entries.contains(entry)
        }
    }

    override fun publish(event: EventEnvelope) {
        entries.forEach { entry ->
            if (!entry.filter.matches(event)) return@forEach
            try {
                entry.subscriber(event)
            } catch (t: Throwable) {
                failures += entry.id
            }
        }
    }

    override fun subscriberCount(): Int = entries.size

    /** Subscribers that threw while handling an event. Diagnostic only. */
    fun failedSubscriberCount(): Int = failures.size
}