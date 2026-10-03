package dev.vitngan.harness.core.report

import dev.vitngan.harness.core.event.EventEnvelope

/**
 * Turns a raw [EventEnvelope] into an [ExecutionSummary].
 *
 * This is the trust boundary for the activity feed. Events are untrusted
 * input (S7); the mapper keeps only a fixed set of known fields and never
 * invents content, so a crafted event cannot smuggle arbitrary text into the
 * UI as if it were system narration.
 */
object ActivityMapper {

    /** Event type -> human label. Unknown types render as the raw type. */
    private val LABELS: Map<String, String> = mapOf(
        "task.created" to "Task created",
        "task.transition" to "Task status changed",
        "policy.decision" to "Policy decision",
        "policy.denied" to "Policy denial",
        "workspace.operation" to "Workspace operation",
        "tool.dispatch" to "Tool dispatched",
        "security.violation" to "Security violation",
        "checkpoint.saved" to "Checkpoint saved",
    )

    /** Events that must be surfaced prominently as failures. */
    private val VIOLATIONS = setOf("security.violation", "policy.denied")

    fun label(type: String): String = LABELS[type] ?: type

    fun isViolation(type: String): Boolean = type in VIOLATIONS

    /**
     * Builds a summary for one event.
     *
     * Only `detail` keys the producer explicitly marked as presentable are
     * surfaced. Unknown keys are dropped rather than rendered.
     */
    fun toSummary(event: EventEnvelope): ExecutionSummary {
        val label = label(event.type)
        val detail = event.payload["detail"]?.takeIf { it.isNotBlank() }
        val subject = event.payload["subject"]?.takeIf { it.isNotBlank() }
        val described = listOfNotNull(subject?.let { "$label: $it" }, detail).joinToString("; ")

        return when {
            isViolation(event.type) -> ExecutionSummary(
                objective = label,
                evidence = listOfNotNull(described.takeIf { it.isNotBlank() }),
                hypothesis = null,
                nextAction = "Blocked by trusted policy. No action taken.",
            )
            event.type == "task.transition" -> ExecutionSummary(
                objective = label,
                evidence = listOfNotNull(described.takeIf { it.isNotBlank() }),
                nextAction = "Continue with the next task step.",
            )
            else -> ExecutionSummary(
                objective = label,
                evidence = listOfNotNull(described.takeIf { it.isNotBlank() }),
                nextAction = null,
            )
        }
    }
}