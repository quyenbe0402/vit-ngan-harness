package dev.vitngan.harness.core.report

import kotlinx.serialization.Serializable

/**
 * A structured, presentable account of one unit of agent activity.
 *
 * This is the **only** shape in which reasoning reaches the UI.
 *
 * Deliberately absent: any field carrying private chain-of-thought. Every
 * member here is an artefact the system can justify - what was attempted,
 * what was observed, what changed, what failed and what happens next. There
 * is no "thoughts" or "reasoning" field, so there is nothing for a UI to
 * leak even by accident.
 */
@Serializable
data class ExecutionSummary(
    /** What this unit of work is for. */
    val objective: String = "",

    /** What was actually observed, stated as fact. */
    val evidence: List<String> = emptyList(),

    /** The candidate cause. Explicitly labelled as a hypothesis, not a fact. */
    val hypothesis: String? = null,

    /** Symbols or paths that were inspected. */
    val inspectedSymbols: List<String> = emptyList(),

    /** The method used. */
    val approach: String? = null,

    /** Files or state that were changed. */
    val changes: List<String> = emptyList(),

    /** Errors encountered. */
    val errors: List<String> = emptyList(),

    /** What was done about the errors. */
    val recovery: List<String> = emptyList(),

    /** The immediate next action. */
    val nextAction: String? = null,
) {
    /** True when nothing has been reported yet. */
    val isEmpty: Boolean
        get() = objective.isBlank() &&
            evidence.isEmpty() && hypothesis == null &&
            inspectedSymbols.isEmpty() && approach == null &&
            changes.isEmpty() && errors.isEmpty() &&
            recovery.isEmpty() && nextAction == null

    /** True when the summary reports a problem. */
    val hasErrors: Boolean get() = errors.isNotEmpty()

    companion object {
        val EMPTY = ExecutionSummary()
    }
}