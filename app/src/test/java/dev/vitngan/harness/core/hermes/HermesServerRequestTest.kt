package dev.vitngan.harness.core.hermes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Server->client request correlation.
 *
 * Upstream treats these as genuine request/response - `dispatch()` routes an
 * inbound response frame to `server_requests.resolve_response` and returns None,
 * emitting no reply of its own - so they are modelled as pending requests with
 * correlated answers, never as notifications.
 */
class HermesServerRequestTest {

    private val adapter = HermesProtocolAdapter()

    private fun idOf(frame: String) = (HermesFraming.decode(frame) as HermesInbound.ServerRequest).frame.id

    private fun readyBridge(now: () -> Long = { 1_000L }): Pair<HermesBridge, FakeHermesTransport> {
        val transport = FakeHermesTransport()
        val bridge = HermesBridge(transport, adapter, now, 5_000L)
        bridge.connect()
        bridge.accept(FakeHermesTransport.readyFrame())
        return bridge to transport
    }

    private fun clarifyFrame(id: String) =
        """{"jsonrpc":"2.0","id":"$id","method":"clarify","params":{"session_id":"s1",""" +
            "\"questions\":[{\"qid\":\"q1\",\"question\":\"which one?\"}]}}"

    @Test
    fun `a server request is held pending, not dropped`() {
        val (bridge, _) = readyBridge()
        bridge.accept(clarifyFrame("srq-1"))

        assertEquals(1, bridge.serverRequestCount())
        val request = bridge.pendingServerRequest("srq-1")!!
        assertEquals("clarify", request.method)
        assertEquals("s1", request.sessionId)
    }

    @Test
    fun `a server request is never mistaken for a notification`() {
        val (bridge, _) = readyBridge()
        bridge.accept(clarifyFrame("srq-1"))
        assertEquals("it must not land in the event stream", 0, bridge.pendingEvents())
    }

    @Test
    fun `answering correlates on the gateway's own id`() {
        val (bridge, transport) = readyBridge()
        bridge.accept(clarifyFrame("srq-1"))

        val result = bridge.answerServerRequest("srq-1", HermesJson.parseObject("""{"answers":{"q1":"b"}}"""))
        assertTrue(result.isSuccess)
        assertEquals(0, bridge.serverRequestCount())
        assertTrue(transport.anySentContaining("\"id\":\"srq-1\""))
        assertTrue("the answer carries no method", transport.lastSent()!!.contains("\"result\""))
    }

    @Test
    fun `answering an unknown id is an orphan, not a silent success`() {
        val (bridge, _) = readyBridge()
        val result = bridge.answerServerRequest("nope", HermesJson.emptyObject())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is HermesBridgeError.OrphanResponse)
    }

    @Test
    fun `a second answer to the same id is rejected`() {
        val (bridge, _) = readyBridge()
        bridge.accept(clarifyFrame("srq-1"))
        assertTrue(bridge.answerServerRequest("srq-1", HermesJson.emptyObject()).isSuccess)
        assertTrue(bridge.answerServerRequest("srq-1", HermesJson.emptyObject()).isFailure)
    }

    @Test
    fun `request_cancel withdraws a held request`() {
        val (bridge, _) = readyBridge()
        bridge.accept(clarifyFrame("srq-1"))

        bridge.accept(
            """{"method":"request.cancel","params":{"payload":{"id":"srq-1",""" +
                "\"method\":\"clarify\",\"reason\":\"session_closed\"}}}"
        )
        assertEquals(0, bridge.serverRequestCount())
    }

    @Test
    fun `a request_cancel for an unknown id is harmless`() {
        val (bridge, _) = readyBridge()
        bridge.accept("""{"method":"request.cancel","params":{"payload":{"id":"ghost","method":"clarify"}}}""")
        assertEquals(0, bridge.serverRequestCount())
    }

    @Test
    fun `an unanswered request expires rather than lingering forever`() {
        var now = 1_000L
        val (bridge, _) = readyBridge { now }
        bridge.accept(clarifyFrame("srq-1"))

        now += 10_000L
        assertEquals(1, bridge.expireStaleServerRequests())
        assertEquals(0, bridge.serverRequestCount())
    }

    @Test
    fun `an answered request is not expired`() {
        var now = 1_000L
        val (bridge, _) = readyBridge { now }
        bridge.accept(clarifyFrame("srq-1"))
        bridge.answerServerRequest("srq-1", HermesJson.emptyObject())
        now += 10_000L
        assertEquals(0, bridge.expireStaleServerRequests())
    }

    @Test
    fun `a settled server request reports the cancellation reason`() {
        var settled: Result<kotlinx.serialization.json.JsonObject>? = null
        val request = PendingServerRequest(
            requestId = "srq-2",
            method = "clarify",
            sessionId = "s1",
            createdAtMillis = 0L,
            timeoutMillis = 1L,
            onSettled = { settled = it },
        )
        request.withdraw("session_closed")

        val error = settled!!.exceptionOrNull() as HermesBridgeError.RequestCancelled
        assertTrue(error.message!!.contains("clarify"))
        assertTrue(error.message!!.contains("session_closed"))
        assertTrue(request.cancelled)
    }

    @Test
    fun `disconnect fails every held server request`() {
        val (bridge, _) = readyBridge()
        bridge.accept(clarifyFrame("srq-1"))
        bridge.disconnect()
        assertEquals(0, bridge.serverRequestCount())
        assertNull(bridge.pendingServerRequest("srq-1"))
    }

    @Test
    fun `pending server requests are listable for a policy UI`() {
        val (bridge, _) = readyBridge()
        bridge.accept(clarifyFrame("a"))
        bridge.accept(
            """{"id":"b","method":"approval","params":{"session_id":"s1","request_id":"r1"}}""",
        )
        assertEquals(listOf("a", "b"), bridge.pendingServerRequests().map { it.requestId })
        assertEquals("approval", bridge.pendingServerRequest("b")!!.method)
    }
}
