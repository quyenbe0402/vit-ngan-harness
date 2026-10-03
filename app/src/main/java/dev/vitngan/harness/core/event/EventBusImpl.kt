package dev.vitngan.harness.core.event

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory [EventBus] with replay.
 *
 * Every published event is recorded in an [EventLog] so a late subscriber can
 * catch up via [replay] without the producer retaining history itself.
 *
 * A throwing subscriber never blocks delivery to others and never propagates
 * to the publisher: event delivery must not be able to break the operation
 * that produced the event.
 */
class EventBusImpl(
    private val log: EventLog = EventLog(),
) : EventBus {

    private data class Entry(
        val id: Long,
        val filter: EventFilter,
        val subscriber: (EventEnvelope) -> Unit,
    )

    private val entries = CopyOnWriteArrayList<Entry>()
    private val ids = AtomicLong(0)
    private val failures = ConcurrentHashMap.newKeySet<Long>()

    /** Last event each subscriber saw, used as its replay anchor. */
    private val lastSeen = ConcurrentHashMap<Long, String>()

    override fun subscribe(filter: EventFilter, subscriber: (EventEnvelope) -> Unit): Subscription {
        val entry = Entry(ids.incrementAndGet(), filter, subscriber)
        entries += entry
        return object : Subscription {
            override fun unsubscribe() {
                entries.remove(entry)
                lastSeen.remove(entry.id)
            }

            override val isActive: Boolean get() = entries.contains(entry)
        }
    }

    override fun publish(event: EventEnvelope) {
        log.append(event)
        entries.forEach { entry ->
            if (!entry.filter.matches(event)) return@forEach
            try {
                entry.subscriber(event)
                lastSeen[entry.id] = event.id
            } catch (t: Throwable) {
                failures += entry.id
            }
        }
    }

    override fun subscriberCount(): Int = entries.size

    /** Subscribers that threw while handling an event. Diagnostic only. */
    fun failedSubscriberCount(): Int = failures.size

    /** The replay log. */
    fun eventLog(): EventLog = log

    /** Events retained so far. */
    fun recordedCount(): Int = log.size()

    /**
     * Replays retained events matching [filter] to [subscriber].
     *
     * [afterId] anchors the replay; when null the whole retained window is
     * delivered. Returns a result whose `truncated` flag tells the caller
     * whether a gap exists, so a missed event is never mistaken for a quiet
     * period.
     */
    fun replay(
        filter: EventFilter = EventFilter(),
        afterId: String? = null,
        subscriber: (EventEnvelope) -> Unit,
    ): EventLog.ReplayResult {
        val result = log.replay(filter, afterId)
        result.events.forEach(subscriber)
        return result
    }
}