package dev.vitngan.harness.core.hermes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Serialisation tolerance.
 *
 * The gateway is a moving peer: it will send fields we have not read and omit
 * ones we expect. Neither may crash the client, because a bridge that throws on
 * an unknown key is a bridge that breaks on the next upstream release.
 */
class HermesSerializationTest {

    private val adapter = HermesProtocolAdapter()

    @Test
    fun `an unknown field in a result is ignored`() {
        val inbound = HermesFraming.decode(
            """{"jsonrpc":"2.0","id":"1","result":{"session_id":"live","future_field":42}}""",
        )
        val created = adapter.sessionCreated((inbound as HermesInbound.Response).frame.result)
        assertEquals("live", created!!.sessionId)
    }

    @Test
    fun `an unknown top-level frame key is ignored`() {
        val inbound = HermesFraming.decode(
            """{"jsonrpc":"2.0","method":"message.delta","trace":"x","params":{"payload":{"text":"a"}}}""",
        )
        assertTrue(inbound is HermesInbound.Notification)
    }

    @Test
    fun `an event with no params decodes to an empty payload`() {
        val inbound = HermesFraming.decode("""{"method":"voice.interrupted"}""")
        val notification = inbound as HermesInbound.Notification
        assertEquals("voice.interrupted", notification.method)
        assertTrue(notification.payload.isEmpty())
    }

    @Test
    fun `a params value of the wrong shape degrades to empty`() {
        val inbound = HermesFraming.decode("""{"method":"message.delta","params":"oops"}""")
        assertTrue((inbound as HermesInbound.Notification).payload.isEmpty())
    }

    @Test
    fun `a deeply nested frame round-trips through encode and decode`() {
        val original = adapter.promptSubmit("s1", "line1\nline2 with \"quotes\" and é")
        val decoded = HermesFraming.decode(original)
        val params = (decoded as HermesInbound.ServerRequest).frame.params
        assertEquals("s1", params.stringOrNull("session_id"))
        assertEquals("line1\nline2 with \"quotes\" and é", params.stringOrNull("prompt"))
    }

    @Test
    fun `unicode survives a round trip`() {
        val text = "héllo — 世界 🌍"
        val frame = adapter.promptSubmit("s", text)
        assertTrue(frame.contains(text))
        assertEquals(text, (HermesFraming.decode(frame) as HermesInbound.ServerRequest).frame.params.stringOrNull("prompt"))
    }

    @Test
    fun `an empty prompt is encoded rather than dropped`() {
        assertTrue(adapter.promptSubmit("s", "").contains("\"prompt\":\"\""))
    }

    @Test
    fun `an error frame with a null id decodes`() {
        val inbound = HermesFraming.decode("""{"jsonrpc":"2.0","id":null,"error":{"code":-32700,"message":"parse"}}""")
        assertNull((inbound as HermesInbound.Error).frame.id)
        assertEquals(-32700, inbound.frame.code)
    }

    @Test
    fun `encodeError produces a parseable frame`() {
        val inbound = HermesFraming.decode(HermesFraming.encodeError("5", 4000, "bad params"))
        assertTrue(inbound is HermesInbound.Error)
        assertEquals("5", (inbound as HermesInbound.Error).frame.id)
        assertEquals(4000, inbound.frame.code)
    }

    @Test
    fun `an encoded server response carries the result and no method`() {
        val frame = HermesFraming.encodeResponse("7", HermesJson.parseObject("""{"answers":{"q1":"b"}}"""))
        val decoded = HermesFraming.decode(frame)
        assertTrue(decoded is HermesInbound.Response)
        val response = (decoded as HermesInbound.Response).frame
        assertEquals("7", response.id)
        assertEquals("b", response.result["answers"].asObject()!!.stringOrNull("q1"))
        assertFalse(frame.contains("\"method\""))
    }

    @Test
    fun `whitespace around a frame is tolerated`() {
        assertTrue(HermesFraming.decode("  \n {\"method\":\"message.start\"}  \t ") is HermesInbound.Notification)
    }

    @Test
    fun `a payload field of the wrong type is read as absent`() {
        assertNull(HermesJson.parseObject("""{"a":{"b":1}}""").stringOrNull("a"))
        assertEquals(1, HermesJson.parseObject("""{"a":{"b":1}}""")["a"].asObject()!!.intOrNull("b"))
    }

    @Test
    fun `numeric helpers degrade on non-numeric input`() {
        val obj = HermesJson.parseObject("""{"i":"x","l":null}""")
        assertNull(obj.intOrNull("i"))
        assertNull(obj.longOrNull("l"))
        assertNull(obj.intOrNull("absent"))
    }

    @Test
    fun `a top-level non-object is rejected as a frame`() {
        assertTrue(runCatching { HermesJson.parseObject("[1,2]") }.isFailure)
        assertTrue(runCatching { HermesJson.parseObject("\"text\"") }.isFailure)
    }
}
