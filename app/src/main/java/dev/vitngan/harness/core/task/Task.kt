package dev.vitngan.harness.core.task

import kotlinx.serialization.Serializable

/**
 * A unit of agent work.
 *
 * Status changes are only ever applied through [TaskManagerImpl.transition],
 * which validates them against [TaskStatus.canTransitionTo].
 */
@Serializable
data class Task(
    val id: String,
    val title: String,
    val description: String = "",
    val status: TaskStatus = TaskStatus.PENDING,
    val workspaceId: String? = null,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val failureReason: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Task.id must not be blank" }
    }

    val isTerminal: Boolean get() = status.isTerminal
}