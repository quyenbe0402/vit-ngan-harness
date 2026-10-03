package dev.vitngan.harness.core.event

/**
 * Declares which events a subscriber wants.
 *
 * All criteria are optional and combine with AND. A null or empty filter
 * matches everything.
 */
data class EventFilter(
    val types: Set<String> = emptySet(),
    val sources: Set<String> = emptySet(),
    val sinceMillis: Long? = null,
) {
    fun matches(event: EventEnvelope): Boolean {
        if (types.isNotEmpty() && event.type !in types) return false
        if (sources.isNotEmpty() && event.source !in sources) return false
        if (sinceMillis != null && event.timestampMillis < sinceMillis) return false
        return true
    }

    val isEmpty: Boolean get() = types.isEmpty() && sources.isEmpty() && sinceMillis == null
}