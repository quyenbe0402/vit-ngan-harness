package dev.vitngan.harness.core.hermes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Typed error mapping.
 *
 * The rule under test: an upstream error is never flattened away. Every mapped
 * error keeps the gateway's own code or message so the real cause survives.
 */
class HermesBridgeErrorTest {

    private val adapter = HermesProtocolAdapter()

    private fun idOf(frame: String) = (HermesFraming.decode(frame) as HermesInbound.ServerRequest).frame.id

    private fun sendOne(bridge: HermesBridge): String {
        val frame = adapter.promptSubmit("s", "hi")
        val id = idOf(frame)
        bridge.send(frame, id, HermesProtocol.Method.PROMPT_SUBMIT, "s")
        return id
    }

    private fun bridge() = HermesBridge(FakeHermesTransport(), adapter, { 1_000L }, 5_000L)

    @Test
    fun `an unknown method maps to ProtocolError and names the method`() {
        val bridge = bridge()
        val id = sendOne(bridge)
        bridge.accept("""{"jsonrpc":"2.0","id":"$id","error":{"code":-32601,"message":"unknown method: x"}}""")
        val error = bridge.await(id)!!.exceptionOrNull() as HermesBridgeError.ProtocolError
        assertEquals(-32601, error.upstreamCode)
        assertTrue(error.message!!.contains(HermesProtocol.Method.PROMPT_SUBMIT))
    }

    @Test
    fun `a params violation maps to ProtocolError with the gateway text`() {
        val bridge = bridge()
        val id = sendOne(bridge)
        bridge.accept("""{"jsonrpc":"2.0","id":"$id","error":{"code":4000,"message":"extra key 'foo'"}}""")
        val error = bridge.await(id)!!.exceptionOrNull() as HermesBridgeError.ProtocolError
        assertEquals(4000, error.upstreamCode)
        assertTrue("the upstream detail must survive", error.message!!.contains("extra key 'foo'"))
    }

    @Test
    fun `any other code maps to RemoteError preserving code and message`() {
        val bridge = bridge()
        val id = sendOne(bridge)
        bridge.accept("""{"jsonrpc":"2.0","id":"$id","error":{"code":-32000,"message":"handler error: boom"}}""")
        val error = bridge.await(id)!!.exceptionOrNull() as HermesBridgeError.RemoteError
        assertEquals(-32000, error.upstreamCode)
        assertEquals("handler error: boom", error.message)
    }

    @Test
    fun `a retiring backend is a RemoteError, not a transport fault`() {
        val bridge = bridge()
        val id = sendOne(bridge)
        bridge.accept("""{"jsonrpc":"2.0","id":"$id","error":{"code":5035,"message":"backend is retiring"}}""")
        val error = bridge.await(id)!!.exceptionOrNull() as HermesBridgeError.RemoteError
        assertEquals(5035, error.upstreamCode)
    }

    @Test
    fun `an error with no code still yields a usable typed failure`() {
        val bridge = bridge()
        val id = sendOne(bridge)
        bridge.accept("""{"jsonrpc":"2.0","id":"$id","error":{"message":"code-less"}}""")
        val error = bridge.await(id)!!.exceptionOrNull() as HermesBridgeError.RemoteError
        assertEquals(HermesProtocol.ErrorCode.SERVER_ERROR, error.upstreamCode)
        assertEquals("code-less", error.message)
    }

    @Test
    fun `error data is retained when present`() {
        val frame = HermesFraming.decode(
            """{"jsonrpc":"2.0","id":"1","error":{"code":4000,"message":"m","data":{"field":"title"}}}""",
        ) as HermesInbound.Error
        assertEquals("title", frame.frame.data!!.stringOrNull("field"))
    }

    @Test
    fun `error data is dropped when it is not an object`() {
        val frame = HermesFraming.decode("""{"id":"1","error":{"code":-32000,"message":"m","data":"text"}}""")
            as HermesInbound.Error
        assertEquals(null, frame.frame.data)
    }

    @Test
    fun `malformed JSON is ignored rather than thrown`() {
        val bridge = bridge()
        bridge.accept("{not json at all")
        bridge.accept("<<<>>>")
        bridge.accept("")
        bridge.accept("[1,2,3]")

        assertEquals(4, bridge.ignoredFrameCount)
        assertEquals(0, bridge.pendingCount())
    }

    @Test
    fun `a frame whose method is not a string is ignored`() {
        val bridge = bridge()
        bridge.accept("""{"jsonrpc":"2.0","method":42}""")
        assertEquals(1, bridge.ignoredFrameCount)
    }

    @Test
    fun `a response with neither result nor error is ignored`() {
        val bridge = bridge()
        bridge.accept("""{"jsonrpc":"2.0","id":"1"}""")
        assertEquals(1, bridge.ignoredFrameCount)
    }

    @Test
    fun `the bridge still works after absorbing malformed input`() {
        val bridge = bridge()
        bridge.accept("{broken")
        val id = sendOne(bridge)
        bridge.accept("""{"jsonrpc":"2.0","id":"$id","result":{"ok":true}}""")
        assertTrue(bridge.await(id)!!.isSuccess)
    }

    @Test
    fun `the audited error codes are the ones we assert against`() {
        assertEquals(-32601, HermesProtocol.ErrorCode.UNKNOWN_METHOD)
        assertEquals(-32603, HermesProtocol.ErrorCode.INTERNAL_ERROR)
        assertEquals(-32000, HermesProtocol.ErrorCode.SERVER_ERROR)
        assertEquals(4000, HermesProtocol.ErrorCode.PARAMS_VIOLATION)
        assertEquals(4064, HermesProtocol.ErrorCode.PROFILE_UNAVAILABLE)
        assertEquals(5035, HermesProtocol.ErrorCode.BACKEND_RETIRING)
    }

    @Test
    fun `every error type is catchable as one HermesBridgeError`() {
        val all: List<HermesBridgeError> = listOf(
            HermesBridgeError.TransportUnavailable(),
            HermesBridgeError.MalformedMessage("bad"),
            HermesBridgeError.ProtocolError(4000, "bad params"),
            HermesBridgeError.SessionNotFound("s"),
            HermesBridgeError.RequestTimeout("m", 1L),
            HermesBridgeError.RequestCancelled("m", "why"),
            HermesBridgeError.RemoteError(-32000, "boom"),
            HermesBridgeError.AuthenticationFailure("no"),
            HermesBridgeError.OrphanResponse("id", "late"),
        )
        assertEquals(9, all.size)
        assertFalse("no error may have an empty message", all.any { it.message.orEmpty().isBlank() })
    }

    @Test
    fun `session-not-found and orphan errors are distinct types`() {
        assertFalse(HermesBridgeError.SessionNotFound("s") is HermesBridgeError.OrphanResponse)
        assertTrue(HermesBridgeError.OrphanResponse("x-1", "late") is HermesBridgeError)
    }
}
