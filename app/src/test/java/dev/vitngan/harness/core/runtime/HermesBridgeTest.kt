package dev.vitngan.harness.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M0-002 declares Hermes as a stub. These tests pin that contract precisely:
 * the backends must refuse to pretend to work, so nothing downstream can
 * mistake a stub for a functioning runtime (S8).
 */
class HermesBridgeTest {

    private fun msg(id: String = "m-1") =
        BridgeMessage(id = id, type = "request", content = "{}", timestampMillis = 1000L)

    @Test
    fun `bridge message json roundtrip`() {
        val original = BridgeMessage(
            id = "m-2",
            type = "response",
            content = """{"ok":true}""",
            timestampMillis = 1_700_000_000_000L,
            inReplyTo = "m-1",
        )
        val restored = BridgeMessage.fromJson(original.toJson())
        assertEquals(original, restored)
    }

    @Test
    fun `bridge message rejects blank id`() {
        var threw = false
        try {
            BridgeMessage("", "t", "c", 1L)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("blank id must be rejected", threw)
    }

    @Test
    fun `termux backend is an unsupported stub`() {
        val termux = TermuxRuntimeBackend()
        assertFalse("termux must not claim support at M0-002", termux.isSupported())
        assertEquals(RuntimeHealth.IDLE, termux.health())
        assertFalse("termux start must refuse", termux.start())
    }

    @Test
    fun `termux backend refuses to send`() {
        assertFalse(TermuxRuntimeBackend().send(msg()))
    }

    @Test
    fun `embedded python backend reports unsupported`() {
        val py = EmbeddedPythonRuntimeBackend()
        assertFalse(py.isSupported())
        assertEquals(RuntimeHealth.UNSUPPORTED, py.health())
        assertFalse(py.start())
        assertFalse(py.send(msg()))
        assertTrue(py.drainIncoming().isEmpty())
        assertNotNull(py.unsupportedReason())
    }

    @Test
    fun `runtime manager finds no supported runtime by default`() {
        val manager = RuntimeManager()
        val termux = TermuxRuntimeBackend()
        val py = EmbeddedPythonRuntimeBackend()
        manager.register(termux)
        manager.register(py)

        assertEquals(2, manager.all().size)
        assertTrue("no backend is usable yet", manager.firstSupported() == null)
        assertFalse(manager.start("termux"))
    }

    @Test
    fun `runtime manager reports health per backend`() {
        val manager = RuntimeManager()
        manager.register(EmbeddedPythonRuntimeBackend())
        val snapshot = manager.healthSnapshot()
        assertEquals(RuntimeHealth.UNSUPPORTED, snapshot["embedded-python"])
    }

    @Test
    fun `runtime manager stop is safe when nothing started`() {
        val manager = RuntimeManager()
        manager.register(TermuxRuntimeBackend())
        manager.stopAll()
        assertFalse(manager.isStarted("termux"))
    }

    @Test
    fun `process event serialises`() {
        val e = ProcessEvent("p-1", ProcessEvent.Kind.STARTED, "", 5L)
        assertEquals("p-1", e.processId)
        assertEquals(ProcessEvent.Kind.STARTED, e.kind)
    }
}