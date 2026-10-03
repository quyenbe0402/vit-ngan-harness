package dev.vitngan.harness.core.event

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Event replay and bounded-log behaviour. */
class EventReplayTest {

    private fun event(id: String, type: String = "task.completed", source: String = "task-manager") =
        EventEnvelope(id, type, 1_000L, source, mapOf("k" to id))

    @Test
    fun `bus records published events for replay`() {
        val bus = EventBusImpl()
        bus.publish(event("e-1"))
        bus.publish(event("e-2"))
        assertEquals(2, bus.recordedCount())
    }

    @Test
    fun `replay with no anchor delivers everything retained`() {
        val bus = EventBusImpl()
        bus.publish(event("e-1"))
        bus.publish(event("e-2"))

        val got = mutableListOf<String>()
        val result = bus.replay { got += it.id }

        assertEquals(listOf("e-1", "e-2"), got)
        assertFalse(result.truncated)
    }

    @Test
    fun `replay after an anchor delivers only newer events`() {
        val bus = EventBusImpl()
        bus.publish(event("e-1"))
        bus.publish(event("e-2"))
        bus.publish(event("e-3"))

        val got = mutableListOf<String>()
        bus.replay(afterId = "e-1") { got += it.id }

        assertEquals(listOf("e-2", "e-3"), got)
    }

    @Test
    fun `replay respects the filter`() {
        val bus = EventBusImpl()
        bus.publish(event("e-1", type = "task.completed"))
        bus.publish(event("e-2", type = "task.failed"))

        val got = mutableListOf<String>()
        bus.replay(EventFilter(types = setOf("task.failed"))) { got += it.id }

        assertEquals(listOf("e-2"), got)
    }

    @Test
    fun `a subscriber that fell behind is told the replay is truncated`() {
        val log = EventLog(capacity = 2)
        log.append(event("e-1"))
        log.append(event("e-2"))
        log.append(event("e-3")) // evicts e-1

        val result = log.replay(afterId = "e-1")
        assertTrue("an evicted anchor must report truncation", result.truncated)
    }

    @Test
    fun `a present anchor is not truncated`() {
        val log = EventLog(capacity = 5)
        log.append(event("e-1"))
        log.append(event("e-2"))

        val result = log.replay(afterId = "e-1")
        assertFalse(result.truncated)
        assertEquals(listOf("e-2"), result.events.map { it.id })
    }

    @Test
    fun `log is bounded and evicts oldest first`() {
        val log = EventLog(capacity = 3)
        repeat(5) { log.append(event("e-$it")) }

        assertEquals(3, log.size())
        assertEquals(listOf("e-2", "e-3", "e-4"), log.all().map { it.id })
        assertEquals("e-2", log.oldestEventId)
    }

    @Test
    fun `replay into a fresh subscriber brings it up to date`() {
        val bus = EventBusImpl()
        bus.publish(event("e-1"))
        bus.publish(event("e-2"))

        val late = mutableListOf<String>()
        bus.replay { late += it.id }
        assertEquals(listOf("e-1", "e-2"), late)
    }

    @Test
    fun `replay does not disturb live subscribers`() {
        val bus = EventBusImpl()
        var live = 0
        bus.subscribe { live++ }

        bus.publish(event("e-1"))
        bus.replay { }

        assertEquals("replay must not re-trigger live delivery", 1, live)
    }

    @Test
    fun `capacity must be positive`() {
        var threw = false
        try {
            EventLog(capacity = 0)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `clear empties the log`() {
        val log = EventLog()
        log.append(event("e-1"))
        log.clear()
        assertEquals(0, log.size())
    }
}