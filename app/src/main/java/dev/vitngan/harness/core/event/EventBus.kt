package dev.vitngan.harness.core.event

/** Publish/subscribe for structured events. */
interface EventBus {

    fun subscribe(filter: EventFilter = EventFilter(), subscriber: (EventEnvelope) -> Unit): Subscription

    /**
     * Publishes an event.
     *
     * A throwing subscriber never prevents delivery to the others, and never
     * propagates to the publisher: event delivery must not be able to break
     * the operation that produced the event.
     */
    fun publish(event: EventEnvelope)

    fun subscriberCount(): Int
}

interface Subscription {
    fun unsubscribe()
    val isActive: Boolean
}