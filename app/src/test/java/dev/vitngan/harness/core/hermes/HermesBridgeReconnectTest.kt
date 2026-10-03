package dev.vitngan.harness.core.hermes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reconnect and replay, driven by the audited `session.events.since` shape.
 */
class HermesBridgeReconnectTest {

    private val adapter = HermesProtocolAdapter()

    private fun bridge(transport: FakeHermesTransport) =
        HermesBridge(transport, adapter, { 1_000L }, 5_000L)

    private fun ready(bridge: HermesBridge, epoch: String = "epoch-1") {
        bridge.accept(FakeHermesTransport.readyFrame(epoch))
    }

    @Test
    fun `the watermark only ever moves forward`() {
        val bridge = bridge(FakeHermesTransport())
        bridge.recordWatermark(10L)
        bridge.recordWatermark(25L)
        bridge.recordWatermark(5L)
        assertEquals(25L, bridge.replayWatermark())
    }

    @Test
    fun `the epoch is captured from gateway ready`() {
        val bridge = bridge(FakeHermesTransport())
        ready(bridge, "ep-42")
        assertEquals("ep-42", bridge.replayEpoch())
    }

    @Test
    fun `a gateway restart on a new epoch resets the watermark`() {
        val bridge = bridge(FakeHermesTransport())
        ready(bridge, "ep-1")
        bridge.recordWatermark(99L)

        ready(bridge, "ep-2")
        assertEquals("a stale watermark would corrupt replay", 0L, bridge.replayWatermark())
    }

    @Test
    fun `the same epoch preserves the watermark`() {
        val bridge = bridge(FakeHermesTransport())
        ready(bridge, "ep-1")
        bridge.recordWatermark(99L)
        ready(bridge, "ep-1")
        assertEquals(99L, bridge.replayWatermark())
    }

    @Test
    fun `resetting connection state clears everything scoped to the connection`() {
        val transport = FakeHermesTransport()
        val bridge = bridge(transport)
        ready(bridge)
        bridge.registerSession(HermesSessionRef("h-1", "t-1", "live", "stored"))
        bridge.accept("""{"method":"message.delta","params":{"payload":{"text":"x"}}}""")

        bridge.resetConnectionState()
        assertNull(bridge.ready)
        assertFalse(bridge.isReady)
        assertEquals(0, bridge.pendingEvents())
        assertEquals("sessions outlive the connection", 1, bridge.sessionCount())
    }

    @Test
    fun `disconnect reports the transport as closed`() {
        val transport = FakeHermesTransport()
        val bridge = bridge(transport)
        ready(bridge)
        bridge.disconnect()
        assertFalse(transport.isOpen)
        assertNull(bridge.ready)
    }

    @Test
    fun `a dead transport cannot be connected to`() {
        val transport = FakeHermesTransport()
        transport.kill()
        val bridge = bridge(transport)
        assertTrue(runCatching { bridge.connect() }.exceptionOrNull() is HermesBridgeError.TransportUnavailable)
    }

    @Test
    fun `sending on a dead transport fails instead of pretending to succeed`() {
        val transport = FakeHermesTransport()
        val bridge = bridge(transport)
        ready(bridge)
        transport.kill()
        val error = runCatching {
            bridge.send("{}", "1", HermesProtocol.Method.PROMPT_SUBMIT, "s")
        }.exceptionOrNull()
        assertTrue(error is HermesBridgeError.TransportUnavailable)
    }

    @Test
    fun `a fresh connection becomes ready again after a reset`() {
        val bridge = bridge(FakeHermesTransport())
        ready(bridge)
        assertTrue(bridge.isReady)
        bridge.resetConnectionState()
        assertFalse(bridge.isReady)
        ready(bridge, "epoch-2")
        assertTrue(bridge.isReady)
        assertEquals("epoch-2", bridge.replayEpoch())
    }

    @Test
    fun `the replay request carries the current watermark`() {
        val bridge = bridge(FakeHermesTransport())
        bridge.recordWatermark(17L)
        val frame = adapter.sessionEventsSince("s1", bridge.replayWatermark())
        assertTrue(frame.contains("\"method\":\"session.events.since\""))
        assertTrue(frame.contains("\"last_seen\":17"))
        assertTrue(frame.contains("\"session_id\":\"s1\""))
    }

    @Test
    fun `the replay request omits last_seen on a first connect`() {
        val bridge = bridge(FakeHermesTransport())
        assertFalse("a zero watermark must not be sent as last_seen",
            adapter.sessionEventsSince("s1", bridge.replayWatermark().takeIf { it > 0 }).contains("last_seen"))
    }

    @Test
    fun `a truncated replay result tells the caller to refetch`() {
        val inbound = HermesFraming.decode(
            """{"id":"1","result":{"events":[],"latest_seq":5,"truncated":true,"epoch":"e"}}""",
        )
        assertTrue(adapter.replayResult((inbound as HermesInbound.Response).frame.result).truncated)
    }

    @Test
    fun `reconnect health is represented by state, not a guess`() {
        val bridge = bridge(FakeHermesTransport())
        assertEquals(HermesBridgeState.DISCONNECTED, bridge.state)
        bridge.connect()
        assertEquals(HermesBridgeState.WAITING_FOR_READY, bridge.state)
        ready(bridge)
        assertEquals(HermesBridgeState.READY, bridge.state)
    }
}
