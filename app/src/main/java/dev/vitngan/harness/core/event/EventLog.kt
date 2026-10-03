package dev.vitngan.harness.core.event

import java.util.concurrent.CopyOnWriteArrayList

/**
 * A bounded, replayable record of published events.
 *
 * Replay exists so a late subscriber can be brought up to date without the
 * producer having to keep every event forever. The bound is deliberate: an
 * unbounded log on a phone is a slow memory leak.
 *
 * Events are untrusted content (S7). Replaying re-delivers data, never
 * authority.
 */
class EventLog(
    private val capacity: Int = DEFAULT_CAPACITY,
) {

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val events = CopyOnWriteArrayList<EventEnvelope>()

    /** Oldest retained event, or null when the log is empty. */
    @Volatile
    var oldestEventId: String? = null
        private set

    @Synchronized
    fun append(event: EventEnvelope) {
        while (events.size >= capacity) {
            events.removeAt(0)
        }
        events += event
        oldestEventId = events.firstOrNull()?.id
    }

    fun size(): Int = events.size

    fun all(): List<EventEnvelope> = events.toList()

    fun clear() {
        synchronized(this) {
            events.clear()
            oldestEventId = null
        }
    }

    /**
     * Events matching [filter] that a subscriber has not seen.
     *
     * [afterId] is the last event the subscriber processed. When that id has
     * already been evicted, replay starts from the oldest retained event and
     * [replayFromStart] is true, so the caller knows it may have missed
     * events rather than silently believing it is up to date.
     */
    fun replay(
        filter: EventFilter = EventFilter(),
        afterId: String? = null,
    ): ReplayResult {
        val all = events.toList()
        val matching = all.filter { filter.matches(it) }

        if (afterId == null) {
            return ReplayResult(events = matching, truncated = false)
        }

        val index = all.indexOfFirst { it.id == afterId }
        if (index < 0) {
            // The anchor is gone: the subscriber fell behind the window.
            return ReplayResult(events = matching, truncated = true)
        }
        return ReplayResult(events = matching.filter { all.indexOf(it) > index }, truncated = false)
    }

    data class ReplayResult(
        val events: List<EventEnvelope>,
        /** True when the anchor had been evicted and a gap may exist. */
        val truncated: Boolean,
    )

    companion object {
        const val DEFAULT_CAPACITY = 512
    }
}