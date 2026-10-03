package dev.vitngan.harness.core.hermes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Streaming and the chain-of-thought boundary.
 *
 * Upstream really does emit `reasoning.delta`, `reasoning.available` and
 * `thinking.delta`, and `message.complete` really does carry a `reasoning`
 * field. Every one of those must be dropped before the Harness sees it, so the
 * private-reasoning tests below are the security-critical ones here.
 */
class HermesBridgeStreamingTest {

    private val adapter = HermesProtocolAdapter()

    private fun deltaFrame(text: String, verbose: Boolean? = null): String {
        val head = """{"jsonrpc":"2.0","method":"message.delta","params":{"payload":{"text":"""
        val tail = if (verbose != null) ""","verbose":$verbose""" else ""
        return head + "\"" + text + "\"" + tail + "}}}"
    }

    private fun readyBridge(): Pair<HermesBridge, FakeHermesTransport> {
        val transport = FakeHermesTransport()
        val bridge = HermesBridge(transport, adapter, { 1_000L }, 5_000L)
        bridge.connect()
        bridge.accept(FakeHermesTransport.readyFrame())
        return bridge to transport
    }

    @Test
    fun `streamed deltas arrive incrementally and in order`() {
        val (bridge, _) = readyBridge()
        bridge.accept("""{"method":"message.start","params":{}}""")
        bridge.accept(deltaFrame("Hel"))
        bridge.accept(deltaFrame("lo, "))
        bridge.accept(deltaFrame("world"))

        val events = bridge.drainEvents()
        assertEquals(4, events.size)
        assertEquals(HermesProtocol.Event.MESSAGE_START, events[0].method)
        assertEquals("Hel", adapter.coerceActivity(events[1].method, events[1].payload)!!["delta"])
        assertEquals("lo, ", adapter.coerceActivity(events[2].method, events[2].payload)!!["delta"])
        assertEquals("world", adapter.coerceActivity(events[3].method, events[3].payload)!!["delta"])
    }

    @Test
    fun `the final response terminates a turn`() {
        val (bridge, _) = readyBridge()
        bridge.accept("""{"method":"message.complete","params":{"payload":{"text":"done","status":"ok"}}}""")
        val activity = adapter.coerceActivity(
            HermesProtocol.Event.MESSAGE_COMPLETE,
            HermesJson.parseObject("""{"text":"done","status":"ok"}"""),
        )!!
        assertEquals("done", activity["text"])
        assertEquals("ok", activity["status"])
    }

    @Test
    fun `reasoning delta is dropped entirely`() {
        val (bridge, _) = readyBridge()
        bridge.accept(
            """{"method":"reasoning.delta","params":{"payload":{"text":"private","verbose":true}}}""",
        )
        assertEquals("chain-of-thought must never be buffered", 0, bridge.pendingEvents())
        assertEquals(0, bridge.drainEvents().size)
    }

    @Test
    fun `a verbose flag on a visible delta does not make it private`() {
        // verbose only rides on the private streams upstream; a message.delta
        // that carries it is still a visible reply chunk and must be delivered.
        val (bridge, _) = readyBridge()
        bridge.accept(deltaFrame("visible", verbose = true))
        assertEquals(1, bridge.pendingEvents())
    }

    @Test
    fun `reasoning available and thinking delta are dropped too`() {
        val (bridge, _) = readyBridge()
        bridge.accept("""{"method":"reasoning.available","params":{"payload":{"text":"secret plan"}}}""")
        bridge.accept("""{"method":"thinking.delta","params":{"payload":{"text":"more secrets"}}}""")
        assertEquals(0, bridge.pendingEvents())
    }

    @Test
    fun `a private stream mixed into a real one does not leak`() {
        val (bridge, _) = readyBridge()
        bridge.accept(deltaFrame("visible"))
        bridge.accept("""{"method":"reasoning.delta","params":{"payload":{"text":"hidden"}}}""")
        bridge.accept(deltaFrame(" also visible"))

        val events = bridge.drainEvents()
        assertEquals(2, events.size)
        events.forEach {
            val text = adapter.coerceActivity(it.method, it.payload)?.get("delta").orEmpty()
            assertFalse(text.contains("hidden"))
        }
    }

    @Test
    fun `the reasoning field on message complete is never surfaced`() {
        val activity = adapter.coerceActivity(
            HermesProtocol.Event.MESSAGE_COMPLETE,
            HermesJson.parseObject("""{"text":"answer","reasoning":"private rationale","status":"ok"}"""),
        )
        assertNull("no key may carry the reasoning", activity!!["reasoning"])
        assertEquals("answer", activity["text"])
    }

    @Test
    fun `a verbose delta cannot smuggle private text through message delta`() {
        // verbose only rides on private streams upstream; a visible delta that
        // claims it must still expose nothing beyond `text`.
        assertEquals(
            mapOf("delta" to "visible"),
            adapter.coerceActivity(
                HermesProtocol.Event.MESSAGE_DELTA,
                HermesJson.parseObject("""{"text":"visible","verbose":true}"""),
            ),
        )
    }

    @Test
    fun `an unknown event is buffered without crashing`() {
        val (bridge, _) = readyBridge()
        bridge.accept("""{"method":"some.future.event","params":{"payload":{"x":1}}}""")
        assertEquals(1, bridge.pendingEvents())
        val event = bridge.drainEvents().single()
        assertNull(
            "an unknown event has no safe projection yet",
            adapter.coerceActivity(event.method, event.payload),
        )
    }

    @Test
    fun `an error event surfaces only its message`() {
        val (bridge, _) = readyBridge()
        bridge.accept("""{"method":"error","params":{"payload":{"message":"agent init failed"}}}""")
        val event = bridge.drainEvents().single()
        assertEquals(mapOf("message" to "agent init failed"), adapter.coerceActivity(event.method, event.payload))
    }

    @Test
    fun `an event with no payload does not crash the projection`() {
        assertNull(adapter.coerceActivity(HermesProtocol.Event.MESSAGE_DELTA, HermesJson.emptyObject()))
    }

    @Test
    fun `draining empties the buffer`() {
        val (bridge, _) = readyBridge()
        bridge.accept(deltaFrame("x"))
        assertEquals(1, bridge.pendingEvents())
        bridge.drainEvents()
        assertEquals(0, bridge.pendingEvents())
    }

    @Test
    fun `the runtime adapter forwards only safe activity`() {
        val (bridge, _) = readyBridge()
        val runtime = BridgeBackedHermesRuntime(bridge, "fake")
        bridge.accept(deltaFrame("visible text"))
        bridge.accept("""{"method":"reasoning.delta","params":{"payload":{"text":"hidden"}}}""")

        val messages = runtime.drainIncoming()
        assertEquals(1, messages.size)
        assertTrue(messages.single().content.contains("visible text"))
        assertFalse(messages.single().content.contains("hidden"))
    }

    @Test
    fun `the runtime reports READY only when the bridge is ready`() {
        val bridge = HermesBridge(FakeHermesTransport(), adapter, { 1_000L }, 5_000L)
        val runtime = BridgeBackedHermesRuntime(bridge, "fake")
        assertFalse(runtime.isSupported())
        bridge.connect()
        bridge.accept(FakeHermesTransport.readyFrame())
        assertTrue(runtime.isSupported())
        assertEquals(dev.vitngan.harness.core.runtime.RuntimeHealth.READY, runtime.health())
    }

    @Test
    fun `a payload field of the wrong type reads as absent`() {
        val activity = adapter.coerceActivity(
            HermesProtocol.Event.MESSAGE_DELTA,
            HermesJson.parseObject("""{"text":{"nested":true}}"""),
        )
        assertNull(activity)
    }

    @Test
    fun `tool output risk is flagged rather than dropped`() {
        val activity = adapter.coerceActivity(
            HermesProtocol.Event.TOOL_OUTPUT_RISK,
            HermesJson.parseObject("""{"name":"bash"}"""),
        )
        assertEquals("flagged", activity!!["risk"])
    }
}
