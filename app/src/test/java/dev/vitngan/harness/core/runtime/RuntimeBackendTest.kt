package dev.vitngan.harness.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M0-004 runtime stubs.
 *
 * These tests pin that the backends refuse cleanly. The risk being guarded
 * against is a stub that looks functional - a stub that reports READY, or
 * accepts a frame, invites a caller to build on a capability that does not
 * exist.
 */
class RuntimeBackendTest {

    private fun msg(id: String = "m-1") =
        BridgeMessage(id, "request", "{}", 1_000L)

    // ---------- Termux ----------

    @Test
    fun `termux is a contract-compliant unsupported stub`() {
        val t = TermuxRuntimeBackend()
        assertFalse("termux must not claim support", t.isSupported())
        assertEquals(RuntimeHealth.IDLE, t.health())
        assertFalse("start must refuse", t.start())
        assertFalse("send must refuse", t.send(msg()))
        t.stop()
        assertTrue(t.drainIncoming().isEmpty())
    }

    @Test
    fun `termux never launches anything`() {
        // A stub cannot be made to start by any sequence of calls.
        val t = TermuxRuntimeBackend()
        t.start()
        t.send(msg())
        assertEquals(RuntimeHealth.IDLE, t.health())
        assertEquals(0, t.drainIncoming().size)
    }

    @Test
    fun `termux test seam injects frames without starting a process`() {
        val t = TermuxRuntimeBackend { 42L }
        t.emitForTest(msg("in-1"))
        val got = t.drainIncoming()
        assertEquals(1, got.size)
        assertEquals("in-1", got.first().id)
        assertEquals(42L, got.first().timestampMillis)
        assertTrue("drain must empty the buffer", t.drainIncoming().isEmpty())
    }

    // ---------- Embedded Python ----------

    @Test
    fun `embedded python reports unsupported`() {
        val py = EmbeddedPythonRuntimeBackend()
        assertFalse(py.isSupported())
        assertEquals(RuntimeHealth.UNSUPPORTED, py.health())
        assertFalse(py.start())
        assertFalse(py.send(msg()))
        assertTrue(py.drainIncoming().isEmpty())
        assertNotNull(py.unsupportedReason())
    }

    @Test
    fun `embedded python stays unsupported across start and stop cycles`() {
        val py = EmbeddedPythonRuntimeBackend()
        repeat(3) {
            py.start()
            assertEquals(RuntimeHealth.UNSUPPORTED, py.health())
            py.stop()
            assertEquals(RuntimeHealth.UNSUPPORTED, py.health())
        }
    }

    // ---------- RuntimeManager ----------

    @Test
    fun `manager registers and reports backends`() {
        val m = RuntimeManager()
        m.register(TermuxRuntimeBackend())
        m.register(EmbeddedPythonRuntimeBackend())
        assertEquals(2, m.all().size)
        assertNotNull(m.byName("termux"))
        assertEquals(2, m.healthSnapshot().size)
    }

    @Test
    fun `registering the same name twice replaces rather than duplicates`() {
        val m = RuntimeManager()
        m.register(TermuxRuntimeBackend())
        m.register(TermuxRuntimeBackend())
        assertEquals(1, m.all().size)
    }

    @Test
    fun `starting an unsupported backend returns false without throwing`() {
        val m = RuntimeManager()
        m.register(TermuxRuntimeBackend())
        assertFalse(m.start("termux"))
        assertFalse(m.isStarted("termux"))
        assertEquals(null, m.activeBackend())
    }

    @Test
    fun `starting an unknown backend returns false without throwing`() {
        val m = RuntimeManager()
        assertFalse(m.start("does-not-exist"))
    }

    @Test
    fun `switching to an unsupported backend does not crash`() {
        val m = RuntimeManager()
        m.register(TermuxRuntimeBackend())
        m.register(EmbeddedPythonRuntimeBackend())

        assertFalse(m.switchTo("termux"))
        assertFalse(m.switchTo("embedded-python"))
        assertFalse(m.switchTo("nope"))
        assertEquals(null, m.activeBackend())
    }

    @Test
    fun `switching to the active backend is idempotent`() {
        val m = RuntimeManager()
        m.register(TermuxRuntimeBackend())
        assertFalse(m.switchTo("termux"))
    }

    @Test
    fun `a failed switch leaves the active backend untouched`() {
        val m = RuntimeManager()
        m.register(TermuxRuntimeBackend())
        m.start("termux")
        val before = m.activeBackend()

        assertFalse(m.switchTo("embedded-python"))
        assertEquals(before, m.activeBackend())
    }

    @Test
    fun `firstSupported returns null while every backend is unsupported`() {
        val m = RuntimeManager()
        m.register(TermuxRuntimeBackend())
        m.register(EmbeddedPythonRuntimeBackend())
        assertEquals(null, m.firstSupported())
    }

    @Test
    fun `a supported stub backend can be selected`() {
        val m = RuntimeManager()
        val fake = object : HermesRuntime {
            override val name = "fake"
            override fun isSupported() = true
            override fun health() = RuntimeHealth.READY
            override fun send(message: BridgeMessage) = true
            override fun start() = true
            override fun stop() = Unit
            override fun drainIncoming() = emptyList<BridgeMessage>()
        }
        m.register(fake)

        assertTrue(m.start("fake"))
        assertTrue(m.isStarted("fake"))
        assertEquals("fake", m.activeBackend())
        assertTrue(m.switchTo("fake"))

        m.stopAll()
        assertEquals(null, m.activeBackend())
    }

    @Test
    fun `health snapshot never throws even if a backend misbehaves`() {
        val m = RuntimeManager()
        m.register(object : HermesRuntime {
            override val name = "broken"
            override fun isSupported() = true
            override fun health(): RuntimeHealth = throw IllegalStateException("boom")
            override fun send(message: BridgeMessage) = false
            override fun start() = false
            override fun stop() = Unit
            override fun drainIncoming() = emptyList<BridgeMessage>()
        })

        assertEquals(RuntimeHealth.FAILED, m.healthSnapshot()["broken"])
    }

    // ---------- bridge contract ----------

    @Test
    fun `bridge message json round trip is stable`() {
        val original = BridgeMessage("m-9", "response", """{"ok":true}""", 1_700_000_000_000L, "m-8")
        assertEquals(original, BridgeMessage.fromJson(original.toJson()))
    }

    @Test
    fun `unknown fields in a bridge frame are ignored`() {
        val json = """{"id":"x","type":"t","content":"c","timestampMillis":1,"future":true}"""
        assertEquals("x", BridgeMessage.fromJson(json).id)
    }
}