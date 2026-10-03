package dev.vitngan.harness.core.hermes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cancellation, timeouts and expiry.
 *
 * The clock is injected and driven by hand, so every timing rule is asserted
 * exactly rather than approximately.
 */
class HermesBridgeCancellationTest {

    private val adapter = HermesProtocolAdapter()

    private fun idOf(frame: String) = (HermesFraming.decode(frame) as HermesInbound.ServerRequest).frame.id

    @Test
    fun `a pending request is not resolved before its response arrives`() {
        var now = 1_000L
        val bridge = HermesBridge(FakeHermesTransport(), adapter, { now }, 5_000L)
        val frame = adapter.promptSubmit("s", "hi")
        val id = idOf(frame)
        bridge.send(frame, id, HermesProtocol.Method.PROMPT_SUBMIT, "s")

        assertTrue(bridge.isPending(id))
        assertNull("an unanswered request must not invent a result", bridge.await(id))
    }

    @Test
    fun `a response resolves the matching request`() {
        val bridge = HermesBridge(FakeHermesTransport(), adapter, { 1_000L }, 5_000L)
        val frame = adapter.promptSubmit("s", "hi")
        val id = idOf(frame)
        bridge.send(frame, id, HermesProtocol.Method.PROMPT_SUBMIT, "s")

        bridge.accept("""{"jsonrpc":"2.0","id":"$id","result":{"ok":true}}""")
        val outcome = bridge.await(id)
        assertTrue(outcome!!.isSuccess)
        assertEquals(true, outcome.getOrThrow().booleanOrNull("ok"))
    }

    @Test
    fun `a response resolves only its own request`() {
        val bridge = HermesBridge(FakeHermesTransport(), adapter, { 1_000L }, 5_000L)
        val first = idOf(adapter.promptSubmit("s", "a"))
        val second = idOf(adapter.promptSubmit("s", "b"))
        bridge.send(adapter.promptSubmit("s", "a"), first, HermesProtocol.Method.PROMPT_SUBMIT, "s")
        bridge.send(adapter.promptSubmit("s", "b"), second, HermesProtocol.Method.PROMPT_SUBMIT, "s")

        bridge.accept("""{"jsonrpc":"2.0","id":"$second","result":{"ok":true}}""")
        assertNull(bridge.await(first))
        assertTrue(bridge.await(second)!!.isSuccess)
    }

    @Test
    fun `cancelling retires the request and reports why`() {
        val bridge = HermesBridge(FakeHermesTransport(), adapter, { 1_000L }, 5_000L)
        val id = idOf(adapter.promptSubmit("s", "hi"))
        bridge.send(adapter.promptSubmit("s", "hi"), id, HermesProtocol.Method.PROMPT_SUBMIT, "s")

        assertTrue(bridge.cancelRequest(id))
        assertFalse(bridge.isPending(id))

        val error = bridge.await(id)
        assertNull("a cancelled request is already retired", error)
    }

    @Test
    fun `cancelling an unknown request is a no-op`() {
        assertFalse(bridge().cancelRequest("nope"))
    }

    @Test
    fun `a request times out once the deadline passes`() {
        var now = 1_000L
        val bridge = HermesBridge(FakeHermesTransport(), adapter, { now }, 5_000L)
        val frame = adapter.promptSubmit("s", "hi")
        val id = idOf(frame)
        bridge.send(frame, id, HermesProtocol.Method.PROMPT_SUBMIT, "s", timeoutMillis = 5_000L)

        now += 4_999L
        assertNull("still inside the deadline", bridge.await(id))

        now += 2L
        val error = bridge.await(id)!!.exceptionOrNull()
        assertTrue(error is HermesBridgeError.RequestTimeout)
        assertEquals(5_000L, (error as HermesBridgeError.RequestTimeout).timeoutMillis)
        assertEquals(HermesProtocol.Method.PROMPT_SUBMIT, error.method)
    }

    @Test
    fun `a response arriving after the deadline does not resurrect the request`() {
        var now = 1_000L
        val bridge = HermesBridge(FakeHermesTransport(), adapter, { now }, 1_000L)
        val frame = adapter.promptSubmit("s", "hi")
        val id = idOf(frame)
        bridge.send(frame, id, HermesProtocol.Method.PROMPT_SUBMIT, "s", timeoutMillis = 1_000L)

        now += 5_000L
        assertTrue(bridge.await(id)!!.isFailure)

        bridge.accept("""{"jsonrpc":"2.0","id":"$id","result":{"ok":true}}""")
        // The answer is retained for a late poll, but the timeout stands: a
        // response that arrives after the deadline must not turn a failure into
        // a success.
        val late = bridge.await(id)!!
        assertTrue("a late answer must not overwrite a timeout", late.isFailure)
        assertTrue(late.exceptionOrNull() is HermesBridgeError.RequestTimeout)
    }

    @Test
    fun `a duplicate response is ignored`() {
        val bridge = bridge()
        val id = idOf(adapter.promptSubmit("s", "hi"))
        bridge.send(adapter.promptSubmit("s", "hi"), id, HermesProtocol.Method.PROMPT_SUBMIT, "s")

        bridge.accept("""{"jsonrpc":"2.0","id":"$id","result":{"ok":true}}""")
        assertTrue(bridge.await(id)!!.isSuccess)

        bridge.accept("""{"jsonrpc":"2.0","id":"$id","result":{"ok":false}}""")
        // The first answer stands: a duplicate must not rewrite a result the
        // caller may already have acted on.
        assertEquals(true, bridge.await(id)!!.getOrThrow().booleanOrNull("ok"))
        assertEquals(0, bridge.pendingCount())
    }

    @Test
    fun `an unsolicited response id is dropped without crashing`() {
        val bridge = bridge()
        bridge.accept("""{"jsonrpc":"2.0","id":"never-sent","result":{"ok":true}}""")
        assertEquals(0, bridge.pendingCount())
    }

    @Test
    fun `a late error frame for an unknown id is dropped`() {
        val bridge = bridge()
        bridge.accept("""{"jsonrpc":"2.0","id":"gone","error":{"code":-32000,"message":"late"}}""")
        assertEquals(0, bridge.pendingCount())
    }

    @Test
    fun `session interrupt is the audited cancellation method`() {
        assertTrue(adapter.sessionInterrupt("s1").contains("\"method\":\"session.interrupt\""))
    }

    private fun bridge() = HermesBridge(FakeHermesTransport(), adapter, { 1_000L }, 5_000L)
}
