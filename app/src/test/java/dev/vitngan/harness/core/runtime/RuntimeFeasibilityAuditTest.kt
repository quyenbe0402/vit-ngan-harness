package dev.vitngan.harness.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M0-007: the audit's recorded conclusions and the runtime-layer boundary.
 *
 * These tests assert two things. First, that the feasibility verdict matches
 * the upstream facts it was derived from - so if someone relaxes the Python
 * floor upstream, this test fails loudly instead of the doc quietly becoming
 * wrong. Second, that the runtime layer still owns *only* lifecycle: no policy,
 * no workspace authorisation, no capability decisions, no agent loop.
 */
class RuntimeFeasibilityAuditTest {

    // ── the recorded verdict ────────────────────────────────────────────────

    @Test
    fun `termux is the selected candidate`() {
        assertEquals(RuntimeCandidate.TERMUX, RuntimeFeasibilityAudit.selected)
    }

    @Test
    fun `termux is feasible with a stated reason`() {
        val finding = RuntimeFeasibilityAudit.finding(RuntimeCandidate.TERMUX)!!
        assertEquals(Feasibility.FEASIBLE, finding.verdict)
        assertTrue("a verdict must carry its reason", finding.reason.isNotBlank())
        assertTrue("a verdict must cite evidence", finding.evidence.isNotEmpty())
    }

    @Test
    fun `embedded python is not feasible for this revision`() {
        val finding = RuntimeFeasibilityAudit.finding(RuntimeCandidate.EMBEDDED_PYTHON)!!
        assertEquals(Feasibility.NOT_FEASIBLE, finding.verdict)
        assertTrue(finding.reason.contains("3.14"))
    }

    @Test
    fun `no hermes runtime is claimed to be running`() {
        assertFalse(
            "M0-007 implemented no runtime; claiming otherwise would be dishonest",
            RuntimeFeasibilityAudit.hermesActuallyRuns(),
        )
    }

    @Test
    fun `every candidate has a finding`() {
        RuntimeCandidate.entries.forEach {
            assertNotNull("missing finding for $it", RuntimeFeasibilityAudit.verdict(it))
        }
    }

    // ── the upstream facts the verdict depends on ───────────────────────────

    @Test
    fun `the audited commit is recorded`() {
        assertEquals("eaecc99c", HermesRuntimeRequirements.AUDITED_COMMIT)
    }

    @Test
    fun `the python floor is recorded because the embedded candidate hinges on it`() {
        assertEquals(">=3.11", HermesRuntimeRequirements.PYTHON_FLOOR)
        assertEquals("3.14", HermesRuntimeRequirements.PYTHON_TARGET)
    }

    @Test
    fun `the gated dependency count is recorded and non-trivial`() {
        // If this drops, an older interpreter may become viable and the
        // embedded-Python rejection must be re-examined.
        assertTrue(HermesRuntimeRequirements.GATED_DEPS_ON_PYTHON_314 > 40)
    }

    @Test
    fun `termux support is recorded as an upstream fact`() {
        assertTrue(HermesRuntimeRequirements.HAS_TERMUX_EXTRAS)
    }

    // ── runtime layer boundary ──────────────────────────────────────────────

    @Test
    fun `no shipped backend claims to be supported`() {
        // No runtime was implemented in M0-007. A backend reporting support
        // would be claiming a Hermes process that does not exist.
        val backends = listOf(TermuxRuntimeBackend(), EmbeddedPythonRuntimeBackend())
        backends.forEach {
            assertFalse("${it.name} must not claim support before a runtime exists", it.isSupported())
        }
    }

    @Test
    fun `no shipped backend reports READY`() {
        val backends = listOf(TermuxRuntimeBackend(), EmbeddedPythonRuntimeBackend())
        backends.forEach {
            // READY would assert a live Hermes process that does not exist.
            assertNotEquals(
                "${it.name} must not report READY before a runtime exists",
                RuntimeHealth.READY,
                it.health(),
            )
        }
    }

    @Test
    fun `no shipped backend starts a process`() {
        assertFalse(TermuxRuntimeBackend().start())
        assertFalse(EmbeddedPythonRuntimeBackend().start())
    }

    @Test
    fun `no shipped backend accepts an outbound frame`() {
        val message = BridgeMessage(id = "1", type = "probe", content = "{}", timestampMillis = 0L)
        assertFalse(TermuxRuntimeBackend().send(message))
        assertFalse(EmbeddedPythonRuntimeBackend().send(message))
    }

    @Test
    fun `the runtime package holds no policy engine`() {
        // Structural: the runtime layer decides which runtime handles a frame,
        // never whether it is allowed.
        val names = HermesRuntime::class.java.methods.map { it.parameterTypes.size }
        assertTrue(names.isNotEmpty())
        val fields = HermesRuntime::class.java.declaredFields.map { it.type.simpleName }
        assertFalse(fields.contains("CapabilityManager"))
        assertFalse(fields.contains("TrustedPolicyEngine"))
        assertFalse(fields.contains("WorkspaceBroker"))
    }

    @Test
    fun `the runtime manager still refuses an unsupported backend`() {
        val manager = RuntimeManager()
        manager.register(TermuxRuntimeBackend())
        assertFalse(manager.start("termux"))
        assertNull(manager.activeBackend())
    }

    @Test
    fun `an unknown backend name is refused rather than thrown`() {
        assertFalse(RuntimeManager().start("does-not-exist"))
    }

    @Test
    fun `no runtime declares an agent loop`() {
        // A second agent loop in the harness would duplicate Hermes' core.
        val sources = listOf(
            TermuxRuntimeBackend::class.java,
            EmbeddedPythonRuntimeBackend::class.java,
            BridgeBackedRuntimeContract::class.java,
        )
        sources.forEach {
            assertFalse(
                it.simpleName + " must not declare agent-loop methods",
                it.methods.any { m -> m.name.contains("agentLoop", true) || m.name == "runAgent" },
            )
        }
    }
}

/** Marker used only to keep the reflection test above honest about its inputs. */
internal interface BridgeBackedRuntimeContract
