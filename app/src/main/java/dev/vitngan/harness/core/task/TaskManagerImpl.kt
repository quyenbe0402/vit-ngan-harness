package dev.vitngan.harness.core.task

import dev.vitngan.harness.core.persistence.TaskStore
import java.util.concurrent.atomic.AtomicLong

/**
 * [TaskManager] over a pluggable [TaskStore].
 *
 * The default store is in-memory; a durable store can be supplied later. Every
 * status change goes through [transition], which refuses illegal moves, and
 * there is no setter on [Task] that bypasses this - an illegal transition
 * cannot happen by accident anywhere in the codebase.
 *
 * Persistence is applied on write, so a restored task carries the same
 * validated status it had before.
 */
class TaskManagerImpl(
    private val store: TaskStore = TaskStore.InMemory(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { "task-${counter.incrementAndGet()}" },
) : TaskManager {

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
        store.put(task)
        return task
    }

    override fun get(id: String): Task? = store.get(id)

    override fun all(): List<Task> = store.all()

    override fun transition(id: String, next: TaskStatus, reason: String?): Task? {
        val current = store.get(id) ?: return null
        if (!current.status.canTransitionTo(next)) return null

        val updated = current.copy(
            status = next,
            updatedAtMillis = clock(),
            failureReason = if (next == TaskStatus.FAILED) reason else null,
        )
        store.put(updated)
        return updated
    }

    override fun cancel(id: String, reason: String?): Task? =
        transition(id, TaskStatus.CANCELLED, reason)

    /** Removes a task. Used by retention policy; not part of the core contract. */
    fun delete(id: String): Boolean = store.delete(id)

    /** Persisted task count. Diagnostic. */
    fun count(): Int = store.count()

    private companion object {
        val counter = AtomicLong(0)
    }
}