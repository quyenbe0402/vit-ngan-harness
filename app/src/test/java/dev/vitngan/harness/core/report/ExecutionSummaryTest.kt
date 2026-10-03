package dev.vitngan.harness.core.report

import dev.vitngan.harness.core.event.EventEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The summary contract and the event -> summary mapping.
 *
 * The security-relevant assertion is structural: [ExecutionSummary] has no
 * field that could carry private reasoning, so there is nothing for a UI to
 * leak. A future field addition must fail these tests by needing a decision.
 */
class ExecutionSummaryTest {

    private fun event(
        id: String = "e-1",
        type: String = "workspace.operation",
        payload: Map<String, String> = mapOf("subject" to "listed 3 entries"),
    ) = EventEnvelope(id, type, 1_000L, "ui", payload)

    @Test
    fun `all required summary fields are present`() {
        val s = ExecutionSummary(
            objective = "o",
            evidence = listOf("e"),
            hypothesis = "h",
            inspectedSymbols = listOf("Sym"),
            approach = "a",
            changes = listOf("c"),
            errors = listOf("err"),
            recovery = listOf("rec"),
            nextAction = "n",
        )
        assertEquals("o", s.objective)
        assertEquals(listOf("e"), s.evidence)
        assertEquals("h", s.hypothesis)
        assertEquals(listOf("Sym"), s.inspectedSymbols)
        assertEquals("a", s.approach)
        assertEquals(listOf("c"), s.changes)
        assertEquals(listOf("err"), s.errors)
        assertEquals(listOf("rec"), s.recovery)
        assertEquals("n", s.nextAction)
    }

    @Test
    fun `empty summary reports empty`() {
        assertTrue(ExecutionSummary.EMPTY.isEmpty)
        assertFalse(ExecutionSummary(objective = "x").isEmpty)
    }

    @Test
    fun `has errors reflects the errors list`() {
        assertFalse(ExecutionSummary(objective = "x").hasErrors)
        assertTrue(ExecutionSummary(objective = "x", errors = listOf("boom")).hasErrors)
    }

    @Test
    fun `hypothesis is labelled, never asserted as fact`() {
        // The mapper must never move a hypothesis into evidence.
        val s = ActivityMapper.toSummary(event(payload = mapOf("detail" to "observed")))
        assertNull("an observed fact is not a hypothesis", s.hypothesis)
    }

    @Test
    fun `unknown event type falls back to the raw type`() {
        assertEquals("totally.unknown", ActivityMapper.label("totally.unknown"))
    }

    @Test
    fun `known types map to human labels`() {
        assertEquals("Task created", ActivityMapper.label("task.created"))
        assertEquals("Policy denial", ActivityMapper.label("policy.denied"))
        assertEquals("Security violation", ActivityMapper.label("security.violation"))
    }

    @Test
    fun `violation types are flagged`() {
        assertTrue(ActivityMapper.isViolation("security.violation"))
        assertTrue(ActivityMapper.isViolation("policy.denied"))
        assertFalse(ActivityMapper.isViolation("task.created"))
    }

    @Test
    fun `mapping produces a summary with a human objective`() {
        val s = ActivityMapper.toSummary(event(type = "task.created"))
        assertEquals("Task created", s.objective)
        assertTrue(s.evidence.any { it.contains("listed 3 entries") })
    }

    @Test
    fun `unknown payload keys are dropped not rendered`() {
        val s = ActivityMapper.toSummary(
            event(payload = mapOf("secrets" to "should-not-appear", "subject" to "ok")),
        )
        val rendered = s.evidence.joinToString(" ")
        assertTrue(rendered.contains("ok"))
        assertFalse("unknown keys must not be rendered", rendered.contains("should-not-appear"))
    }

    @Test
    fun `a violation summary states that nothing was done`() {
        val s = ActivityMapper.toSummary(
            event(type = "security.violation", payload = mapOf("detail" to "traversal refused")),
        )
        assertNotNull(s.nextAction)
        assertTrue(s.nextAction!!.contains("No action taken"))
    }

    @Test
    fun `an empty payload still produces a labelled summary`() {
        val s = ActivityMapper.toSummary(event(payload = emptyMap()))
        assertEquals("Workspace operation", s.objective)
        assertTrue(s.evidence.isEmpty())
    }
}