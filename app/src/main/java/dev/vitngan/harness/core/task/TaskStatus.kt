package dev.vitngan.harness.core.task

/** Lifecycle of a unit of agent work. */
enum class TaskStatus {
    PENDING,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
    ;

    val isTerminal: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELLED

    /**
     * True when moving from this status to [next] is legal.
     *
     * Terminal states are final: a task cannot be resurrected. Re-running a
     * task means creating a new one, so history stays truthful.
     */
    fun canTransitionTo(next: TaskStatus): Boolean = when (this) {
        PENDING -> next == RUNNING || next == CANCELLED
        RUNNING -> next == PAUSED || next == COMPLETED || next == FAILED || next == CANCELLED
        PAUSED -> next == RUNNING || next == CANCELLED
        COMPLETED, FAILED, CANCELLED -> false
    }
}