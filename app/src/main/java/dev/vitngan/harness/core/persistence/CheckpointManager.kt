package dev.vitngan.harness.core.persistence

import java.util.concurrent.atomic.AtomicLong

/**
 * Saves and restores checkpoints.
 *
 * Backed by a [CheckpointStore], so the same logic serves the in-memory store
 * used in tests and the Room store used on a device.
 *
 * Security invariant S7: a restored payload is **untrusted input**. `restore`
 * returns the raw payload for the caller to re-validate; restoring a
 * checkpoint never re-grants a capability and never bypasses policy.
 */
class CheckpointManager(
    private val store: CheckpointStore = CheckpointStore.InMemory(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { "ckpt-${counter.incrementAndGet()}" },
) {

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
        store.put(checkpoint)
        return checkpoint
    }

    fun get(id: String): Checkpoint? = store.get(id)

    fun forTask(taskId: String): List<Checkpoint> = store.forTask(taskId)

    fun latestForTask(taskId: String): Checkpoint? = store.latestForTask(taskId)

    fun delete(id: String): Boolean = store.delete(id)

    fun clear() = store.clear()

    fun count(taskId: String? = null): Int = store.count(taskId)

    /**
     * Restores a checkpoint for [taskId].
     *
     * Refuses when the checkpoint belongs to a different task. Without this
     * check a task could restore another task's state - a confused-deputy
     * bug that no caller should have to guard against individually.
     */
    fun restore(id: String, taskId: String): Checkpoint? {
        val checkpoint = store.get(id) ?: return null
        if (checkpoint.taskId != taskId) return null
        return checkpoint
    }

    /** Walk from [id] to the root, newest first. Cycle-safe. */
    fun lineage(id: String): List<Checkpoint> {
        val out = mutableListOf<Checkpoint>()
        val seen = mutableSetOf<String>()
        var current = store.get(id)
        while (current != null && seen.add(current.id)) {
            out += current
            current = current.parentId?.let { store.get(it) }
        }
        return out
    }

    private companion object {
        val counter = AtomicLong(0)
    }
}