package dev.vitngan.harness.core.task

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory [TaskManager].
 *
 * Every status change goes through [transition], which refuses illegal moves.
 * There is no setter on [Task] that bypasses this, so an illegal transition
 * cannot happen by accident anywhere in the codebase.
 */
class TaskManagerImpl(
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { "task-${counter.incrementAndGet()}" },
) : TaskManager {

    private val tasks = ConcurrentHashMap<String, Task>()

    override fun create(title: String, description: String, workspaceId: String?): Task {
        require(title.isNotBlank()) { "Task title must not be blank" }
        val now = clock()
        val task = Task(
            id = idGenerator(),
            title = title,
            description = description,
            status = TaskStatus.PENDING,
            workspaceId = workspaceId,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        tasks[task.id] = task
        return task
    }

    override fun get(id: String): Task? = tasks[id]

    override fun all(): List<Task> = tasks.values.sortedBy { it.createdAtMillis }

    override fun transition(id: String, next: TaskStatus, reason: String?): Task? {
        val current = tasks[id] ?: return null
        if (!current.status.canTransitionTo(next)) return null

        val updated = current.copy(
            status = next,
            updatedAtMillis = clock(),
            failureReason = if (next == TaskStatus.FAILED) reason else null,
        )
        tasks[id] = updated
        return updated
    }

    override fun cancel(id: String, reason: String?): Task? =
        transition(id, TaskStatus.CANCELLED, reason)

    private companion object {
        val counter = AtomicLong(0)
    }
}