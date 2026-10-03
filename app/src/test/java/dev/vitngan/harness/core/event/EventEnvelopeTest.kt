package dev.vitngan.harness.core.event

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventEnvelopeTest {

    private fun sample() = EventEnvelope(
        id = "e-1",
        type = "task.completed",
        timestampMillis = 1_700_000_000_000L,
        source = "task-manager",
        payload = mapOf("taskId" to "t1", "status" to "COMPLETED"),
    )

    @Test
    fun `json roundtrip preserves every field`() {
        val original = sample()
        val restored = EventEnvelope.fromJson(original.toJson())

        assertEquals(original.id, restored.id)
        assertEquals(original.type, restored.type)
        assertEquals(original.timestampMillis, restored.timestampMillis)
        assertEquals(original.source, restored.source)
        assertEquals(original.payload, restored.payload)
    }

    @Test
    fun `empty payload roundtrips`() {
        val e = EventEnvelope("e-2", "ping", 1L, "test")
        assertEquals(e, EventEnvelope.fromJson(e.toJson()))
    }

    @Test
    fun `blank id is rejected`() {
        var threw = false
        try {
            EventEnvelope("", "t", 1L, "s")
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("blank id must be rejected", threw)
    }

    @Test
    fun `unknown fields are ignored on parse`() {
        val json = """{"id":"e-3","type":"t","timestampMillis":1,"source":"s","extra":"x"}"""
        val e = EventEnvelope.fromJson(json)
        assertEquals("e-3", e.id)
    }

    @Test
    fun `filter matches on type`() {
        val e = sample()
        assertTrue(EventFilter(types = setOf("task.completed")).matches(e))
        assertTrue(!EventFilter(types = setOf("task.failed")).matches(e))
    }

    @Test
    fun `filter matches on source`() {
        val e = sample()
        assertTrue(EventFilter(sources = setOf("task-manager")).matches(e))
        assertTrue(!EventFilter(sources = setOf("runtime")).matches(e))
    }

    @Test
    fun `filter respects since`() {
        val e = sample()
        assertTrue(EventFilter(sinceMillis = e.timestampMillis).matches(e))
        assertTrue(!EventFilter(sinceMillis = e.timestampMillis + 1).matches(e))
    }

    @Test
    fun `empty filter matches everything`() {
        val filter = EventFilter()
        assertTrue(filter.isEmpty)
        assertTrue(filter.matches(sample()))
    }

    @Test
    fun `bus delivers to matching subscribers only`() {
        val bus = EventBusImpl()
        var aCount = 0
        var bCount = 0
        bus.subscribe(EventFilter(types = setOf("task.completed"))) { aCount++ }
        bus.subscribe(EventFilter(types = setOf("task.failed"))) { bCount++ }

        bus.publish(sample())
        assertEquals(1, aCount)
        assertEquals(0, bCount)
    }

    @Test
    fun `unsubscribe stops delivery`() {
        val bus = EventBusImpl()
        var count = 0
        val sub = bus.subscribe { count++ }
        bus.publish(sample())
        sub.unsubscribe()
        bus.publish(sample())
        assertEquals(1, count)
        assertTrue(!sub.isActive)
    }

    @Test
    fun `a throwing subscriber does not break delivery to others`() {
        val bus = EventBusImpl()
        var delivered = 0
        bus.subscribe { throw RuntimeException("subscriber blew up") }
        bus.subscribe { delivered++ }

        bus.publish(sample())
        assertEquals(1, delivered)
        assertEquals(1, bus.failedSubscriberCount())
    }

    @Test
    fun `subscriber count is reported`() {
        val bus = EventBusImpl()
        assertEquals(0, bus.subscriberCount())
        val s = bus.subscribe { }
        assertEquals(1, bus.subscriberCount())
        s.unsubscribe()
        assertEquals(0, bus.subscriberCount())
    }
}