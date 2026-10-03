package dev.vitngan.harness.core.hermes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Protocol fidelity, checked against frames shaped like the audited upstream
 * wire (NousResearch/hermes-agent @ eaecc99c).
 *
 * These assert on exact JSON text where the wire format matters, because
 * "produces plausible JSON" is not the same as "produces protocol-accurate JSON".
 */
class HermesProtocolTest {

    private val adapter = HermesProtocolAdapter()

    @Test
    fun `prompt submit carries session_id and prompt`() {
        val frame = adapter.promptSubmit("sess-1", "hello")
        assertTrue(frame.contains("\"method\":\"prompt.submit\""))
        assertTrue(frame.contains("\"session_id\":\"sess-1\""))
        assertTrue(frame.contains("\"prompt\":\"hello\""))
        assertTrue(frame.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(frame.contains("\"id\":"))
    }

    @Test
    fun `session interrupt omits the guard when none is supplied`() {
        // Upstream params are extra="forbid"; a null must never be sent.
        val frame = adapter.sessionInterrupt("sess-1")
        assertTrue(frame.contains("\"method\":\"session.interrupt\""))
        assertFalse(frame.contains("expected_hosted_task_id"))
    }

    @Test
    fun `session interrupt includes the guard when supplied`() {
        assertTrue(adapter.sessionInterrupt("sess-1", "task-9").contains("\"expected_hosted_task_id\":\"task-9\""))
    }

    @Test
    fun `session create omits every unspecified field`() {
        val frame = adapter.sessionCreate(SessionCreateSpec())
        assertFalse(frame.contains("title"))
        assertFalse(frame.contains("model"))
        assertFalse(frame.contains("cwd"))
    }

    @Test
    fun `session create emits only the supplied fields`() {
        val frame = adapter.sessionCreate(SessionCreateSpec(title = "t", model = "m"))
        assertTrue(frame.contains("\"title\":\"t\""))
        assertTrue(frame.contains("\"model\":\"m\""))
        assertFalse(frame.contains("provider"))
    }

    @Test
    fun `each request carries a distinct id`() {
        val a = HermesFraming.decode(adapter.promptSubmit("s", "a")) as HermesInbound.ServerRequest
        val b = HermesFraming.decode(adapter.promptSubmit("s", "b")) as HermesInbound.ServerRequest
        assertTrue("ids must be unique per process", a.frame.id != b.frame.id)
    }

    @Test
    fun `gateway ready is decoded from the upstream payload shape`() {
        val inbound = HermesFraming.decode(
            """{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":""" +
                """{"skin":{"name":"default"},"change_events":true,"replay_epoch":"ep-7","heartbeat":true}}}""",
        )
        val ready = adapter.ready((inbound as HermesInbound.Notification).payload)
        assertNotNull(ready)
        assertEquals("ep-7", ready!!.replayEpoch)
        assertTrue(ready.changeEvents)
        assertEquals(true, ready.heartbeat)
    }

    @Test
    fun `session created keeps the live and stored ids apart`() {
        val inbound = HermesFraming.decode(
            "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"session_id\":\"live-1\"," +
                "\"stored_session_id\":\"stored-1\",\"message_count\":3,\"messages\":[],\"info\":{}}}",
        )
        val created = adapter.sessionCreated((inbound as HermesInbound.Response).frame.result)
        assertEquals("live-1", created!!.sessionId)
        assertEquals("stored-1", created.storedSessionId)
        assertEquals(3, created.messageCount)
    }

    @Test
    fun `interrupt status maps the closed upstream set`() {
        fun status(raw: String) = adapter.interruptResult(
            (HermesFraming.decode("""{"id":"1","result":{"status":"$raw"}}""")
                as HermesInbound.Response).frame.result,
        ).status

        assertEquals(HermesInterruptStatus.INTERRUPTED, status("interrupted"))
        assertEquals(HermesInterruptStatus.NOT_INTERRUPTED, status("not_interrupted"))
        assertEquals("a future status must not crash", HermesInterruptStatus.UNKNOWN, status("invented_later"))
    }

    @Test
    fun `a frame with an id and no method is a response, not a request`() {
        val inbound = HermesFraming.decode("""{"jsonrpc":"2.0","id":"9","result":{"ok":true}}""")
        assertTrue(inbound is HermesInbound.Response)
        assertEquals("9", (inbound as HermesInbound.Response).frame.id)
    }

    @Test
    fun `a frame with a method and no id is a notification`() {
        assertTrue(
            HermesFraming.decode("""{"jsonrpc":"2.0","method":"message.start","params":{}}""")
                is HermesInbound.Notification,
        )
    }

    @Test
    fun `a frame with a method and an id is a server request`() {
        val inbound = HermesFraming.decode(
            """{"jsonrpc":"2.0","id":"srq-1","method":"clarify","params":{"session_id":"s1"}}""",
        )
        val frame = (inbound as HermesInbound.ServerRequest).frame
        assertEquals("srq-1", frame.id)
        assertEquals("clarify", frame.method)
        assertEquals("s1", frame.params.stringOrNull("session_id"))
    }

    @Test
    fun `a numeric id is accepted because upstream allows both shapes`() {
        val inbound = HermesFraming.decode("""{"jsonrpc":"2.0","id":42,"result":{"ok":true}}""")
        assertEquals("42", (inbound as HermesInbound.Response).frame.id)
    }

    @Test
    fun `replay result decodes the truncation signal`() {
        val inbound = HermesFraming.decode(
            """{"id":"1","result":{"events":[],"latest_seq":88,"truncated":true,"count":0,"epoch":"ep-7"}}""",
        )
        val replay = adapter.replayResult((inbound as HermesInbound.Response).frame.result)
        assertEquals(88L, replay.latestSeq)
        assertTrue("truncated forces a refetch upstream", replay.truncated)
        assertEquals("ep-7", replay.epoch)
    }

    @Test
    fun `replay events decode as objects`() {
        val inbound = HermesFraming.decode(
            """{"id":"1","result":{"events":[{"method":"message.delta"}],"latest_seq":1,"epoch":"e"}}""",
        )
        assertEquals(1, adapter.replayResult((inbound as HermesInbound.Response).frame.result).events.size)
    }

    @Test
    fun `replay result survives missing optional fields`() {
        val replay = adapter.replayResult(
            (HermesFraming.decode("""{"id":"1","result":{}}""") as HermesInbound.Response).frame.result,
        )
        assertEquals(0L, replay.latestSeq)
        assertFalse(replay.truncated)
    }

    @Test
    fun `null is distinguishable from absent for heartbeat`() {
        val inbound = HermesFraming.decode(
            """{"method":"event","params":{"type":"gateway.ready","payload":""" +
                """{"replay_epoch":"e","change_events":true,"heartbeat":null}}}""",
        )
        assertNull(adapter.ready((inbound as HermesInbound.Notification).payload)!!.heartbeat)
    }

    // ── M0-007B real-frame fixtures ──────────────────────────────────────────
    //
    // These pin the fixture to what a live Hermes stdio gateway actually sent.
    // If readyFrame() ever reverts to the pre-fix shape, these fail.

    @Test
    fun `the ready fixture reproduces the real observed stdio frame`() {
        val frame = FakeHermesTransport.readyFrame()

        assertTrue("envelope method is the literal event", frame.contains(""""method": "event""""))
        assertFalse(
            "the event name must not sit in the envelope method",
            frame.contains(""""method": "gateway.ready""""),
        )
        assertTrue("event name lives in params.type", frame.contains(""""type": "gateway.ready""""))
        assertTrue("payload is nested under params", frame.contains(""""payload""""))
        assertTrue("change_events is present", frame.contains(""""change_events": true"""))
        assertTrue("replay_epoch is present", frame.contains(""""replay_epoch""""))
        assertTrue("skin is a full theme object", frame.contains(""""tool_prefix""""))
        assertTrue("skin carries branding", frame.contains(""""branding""""))
    }

    @Test
    fun `the stdio ready fixture omits heartbeat because ws only emits it`() {
        // tui_gateway/entry.py omits heartbeat; tui_gateway/ws.py sets it true.
        assertFalse(
            "a stdio fixture claiming heartbeat would misrepresent the transport",
            FakeHermesTransport.readyFrame().contains("heartbeat"),
        )
    }

    @Test
    fun `the real ready frame decodes to the gateway_ready notification`() {
        val inbound = HermesFraming.decode(FakeHermesTransport.readyFrame())
        assertTrue(
            "the real frame must decode as a notification, not be dropped",
            inbound is HermesInbound.Notification,
        )
        val notification = inbound as HermesInbound.Notification
        assertEquals(HermesProtocol.Event.GATEWAY_READY, notification.method)
        assertEquals("epoch-1", notification.payload.stringOrNull("replay_epoch"))
        assertEquals(true, notification.payload.booleanOrNull("change_events"))
    }

    @Test
    fun `a real event frame is never routed to a method literally named event`() {
        val inbound = HermesFraming.decode(FakeHermesTransport.readyFrame())
        val notification = inbound as HermesInbound.Notification
        assertFalse(
            "that mis-routing was the original M0-006 defect",
            notification.method == HermesProtocol.METHOD_EVENT,
        )
        assertEquals(HermesProtocol.Event.GATEWAY_READY, notification.method)
    }

    @Test
    fun `an event frame without params_type is rejected rather than guessed`() {
        val inbound = HermesFraming.decode("""{"jsonrpc":"2.0","method":"event","params":{}}""")
        assertTrue(
            "an unnamed event frame must not be silently accepted",
            inbound is HermesInbound.Ignored,
        )
    }
}
