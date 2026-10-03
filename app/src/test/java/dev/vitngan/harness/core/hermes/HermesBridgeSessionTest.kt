package dev.vitngan.harness.core.hermes

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Session lifecycle and the harness/gateway id mapping.
 *
 * The live-vs-stored id asymmetry is the trap the upstream docstring calls out,
 * so it is pinned here explicitly.
 */
class HermesBridgeSessionTest {

    private val adapter = HermesProtocolAdapter()

    private fun bridge() = HermesBridge(FakeHermesTransport(), adapter, { 1_000L }, 5_000L)

    private fun readyBridge(): Pair<HermesBridge, FakeHermesTransport> {
        val transport = FakeHermesTransport()
        val bridge = HermesBridge(transport, adapter, { 1_000L }, 5_000L)
        bridge.connect()
        bridge.accept(FakeHermesTransport.readyFrame())
        return bridge to transport
    }

    private fun idOf(frame: String) = (HermesFraming.decode(frame) as HermesInbound.ServerRequest).frame.id

    @Test
    fun `gateway ready promotes the bridge to READY`() {
        val (bridge, _) = readyBridge()
        assertTrue(bridge.isReady)
        assertEquals(HermesBridgeState.READY, bridge.state)
        assertEquals("epoch-1", bridge.replayEpoch())
    }

    @Test
    fun `a bridge is not ready before gateway_ready arrives`() {
        val transport = FakeHermesTransport()
        val bridge = HermesBridge(transport, adapter, { 1_000L }, 5_000L)
        bridge.connect()
        assertFalse(bridge.isReady)
        assertEquals(HermesBridgeState.AWAITING_READY, bridge.state)
        assertTrue("connect pings the gateway", transport.anySentContaining("gateway.ping"))
    }

    @Test
    fun `connecting to a dead transport fails loudly`() {
        val bridge = HermesBridge(FakeHermesTransport(acceptSends = false), adapter, { 1_000L }, 5_000L)
        assertTrue(runCatching { bridge.connect() }.exceptionOrNull() is HermesBridgeError.TransportUnavailable)
    }

    @Test
    fun `a session is created and both gateway ids are captured`() {
        val (bridge, transport) = readyBridge()
        val frame = adapter.sessionCreate(SessionCreateSpec(title = "audit"))
        val id = idOf(frame)
        bridge.send(frame, id, HermesProtocol.Method.SESSION_CREATE)
        assertTrue(transport.anySentContaining("session.create"))

        bridge.accept(
            """{"jsonrpc":"2.0","id":"$id","result":{"session_id":"live-9",""" +
                "\"stored_session_id\":\"stored-9\",\"message_count\":0,\"messages\":[],\"info\":{}}}",
        )

        val result = bridge.await(id)!!.getOrThrow() as JsonObject
        val ref = HermesSessionRef.fromCreated("harness-1", "task-1", adapter.sessionCreated(result)!!)
        bridge.registerSession(ref)

        assertEquals("live-9", bridge.session("harness-1")!!.runtimeSessionId)
        assertEquals("stored-9", bridge.session("harness-1")!!.storedSessionId)
    }

    @Test
    fun `the harness id is never derived from a gateway id`() {
        val ref = HermesSessionRef.fromCreated(
            "harness-uuid",
            "task-uuid",
            HermesSessionCreated("live-1", "stored-1", 0),
        )
        assertEquals("harness-uuid", ref.harnessSessionId)
        assertEquals("task-uuid", ref.taskId)
    }

    @Test
    fun `session resume must be given the stored id, not the live id`() {
        val ref = HermesSessionRef.fromCreated("h", "t", HermesSessionCreated("live-1", "stored-1", 0))
        assertEquals("upstream resume takes the STORED id", "stored-1", ref.resumeId)
        assertFalse(ref.resumeId == ref.runtimeSessionId)
    }

    @Test
    fun `live addressing prefers the runtime id and falls back to stored`() {
        assertEquals("live-1", HermesSessionRef("h", "t", "live-1", "stored-1").liveId)
        assertEquals("stored-1", HermesSessionRef("h", "t", null, "stored-1").liveId)
        assertEquals("stored-1", HermesSessionRef("h", "t", null, "stored-1").requestSessionId)
    }

    @Test
    fun `a ref with no gateway ids addresses nothing`() {
        val empty = HermesSessionRef("h", "t", null, null)
        assertNull(empty.liveId)
        assertNull(empty.resumeId)
    }

    @Test
    fun `closing a session drops the runtime id but keeps the durable one`() {
        val closed = HermesSessionRef.fromCreated("h", "t", HermesSessionCreated("live-1", "stored-1", 0)).closed()
        assertNull(closed.runtimeSessionId)
        assertEquals("stored-1", closed.storedSessionId)
    }


    @Test
    fun `forgetting a session removes it from the bridge`() {
        val bridge = bridge()
        bridge.registerSession(HermesSessionRef("h-1", "t-1", "live", "stored"))
        assertEquals(1, bridge.sessionCount())
        bridge.forgetSession("h-1")
        assertEquals(0, bridge.sessionCount())
        assertNull(bridge.session("h-1"))
    }

    @Test
    fun `a session can be updated in place`() {
        val bridge = bridge()
        bridge.registerSession(HermesSessionRef("h-1", "t-1", null, "stored-1"))
        bridge.updateSession("h-1") { it.withCreated(HermesSessionCreated("live-2", "stored-2", 0)) }
        assertEquals("live-2", bridge.session("h-1")!!.runtimeSessionId)
    }

    @Test
    fun `disconnecting retires every in-flight caller rather than hanging it`() {
        val (bridge, _) = readyBridge()
        val frame = adapter.promptSubmit("s", "hi")
        val id = idOf(frame)
        bridge.send(frame, id, HermesProtocol.Method.PROMPT_SUBMIT, "s")
        assertTrue(bridge.isPending(id))

        bridge.disconnect()
        assertEquals(HermesBridgeState.DISCONNECTED, bridge.state)
        assertFalse(bridge.isPending(id))
        assertEquals(0, bridge.pendingCount())
    }

    @Test
    fun `a gateway ready frame with no payload leaves the bridge not ready`() {
        val bridge = bridge()
        bridge.connect()
        bridge.accept("""{"method":"gateway.ready","params":{}}""")
        assertFalse(bridge.isReady)
    }
}
