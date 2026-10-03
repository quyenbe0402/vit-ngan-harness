package dev.vitngan.harness.core.persistence

import java.util.concurrent.ConcurrentHashMap

/**
 * Stores and restores checkpoints.
 *
 * M0-002 keeps checkpoints in memory; [AppDatabase] provides the durable store.
 * A restored payload is untrusted input and must be re-validated before use
 * (S7) - restoring a checkpoint never re-grants a capability.
 */
class CheckpointManager(
    private val database: AppDatabase? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { "ckpt-${counter.incrementAndGet()}" },
) {

    private val inMemory = ConcurrentHashMap<String, Checkpoint>()

    fun save(
        taskId: String,
        label: String,
        payload: String,
        workspaceId: String? = null,
        parentId: String? = null,
    ): Checkpoint {
        require(taskId.isNotBlank()) { "taskId must not be blank" }
        require(label.isNotBlank()) { "label must not be blank" }
        val checkpoint = Checkpoint(
            id = idGenerator(),
            taskId = taskId,
            label = label,
            createdAtMillis = clock(),
            workspaceId = workspaceId,
            payload = payload,
            parentId = parentId,
        )
        inMemory[checkpoint.id] = checkpoint
        return checkpoint
    }

    fun get(id: String): Checkpoint? = inMemory[id]

    fun forTask(taskId: String): List<Checkpoint> =
        inMemory.values.filter { it.taskId == taskId }.sortedBy { it.createdAtMillis }

    fun latestForTask(taskId: String): Checkpoint? = forTask(taskId).lastOrNull()

    fun delete(id: String): Boolean = inMemory.remove(id) != null

    fun clear() = inMemory.clear()

    fun count(): Int = inMemory.size

    private companion object {
        val counter = java.util.concurrent.atomic.AtomicLong(0)
    }
}