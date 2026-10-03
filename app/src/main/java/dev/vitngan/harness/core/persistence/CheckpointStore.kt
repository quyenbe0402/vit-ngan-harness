package dev.vitngan.harness.core.persistence

/**
 * Storage seam behind [CheckpointManager].
 *
 * Split from the manager so the manager is unit-testable on the JVM without
 * Robolectric or a device, and so a future store can replace the in-memory
 * one without touching the manager.
 */
interface CheckpointStore {

    fun put(checkpoint: Checkpoint)

    fun get(id: String): Checkpoint?

    fun forTask(taskId: String): List<Checkpoint>

    fun latestForTask(taskId: String): Checkpoint?

    fun delete(id: String): Boolean

    fun count(taskId: String? = null): Int

    fun clear()

    /** In-memory store. Default until Room is wired on a device. */
    class InMemory : CheckpointStore {
        private val items = LinkedHashMap<String, Checkpoint>()

        @Synchronized override fun put(checkpoint: Checkpoint) {
            items[checkpoint.id] = checkpoint
        }

        @Synchronized override fun get(id: String): Checkpoint? = items[id]

        @Synchronized override fun forTask(taskId: String): List<Checkpoint> =
            items.values.filter { it.taskId == taskId }.sortedBy { it.createdAtMillis }

        @Synchronized override fun latestForTask(taskId: String): Checkpoint? =
            forTask(taskId).lastOrNull()

        @Synchronized override fun delete(id: String): Boolean = items.remove(id) != null

        @Synchronized override fun count(taskId: String?): Int =
            if (taskId == null) items.size else items.values.count { candidate -> candidate.taskId == taskId }

        @Synchronized override fun clear() = items.clear()
    }

    /**
     * Room-backed store.
     *
     * Used only where a real [AppDatabase] is available. Every read is scoped
     * by task id so one task can never observe another's checkpoints.
     */
    class Room(private val dao: CheckpointDao) : CheckpointStore {
        override fun put(checkpoint: Checkpoint) {
            dao.upsert(checkpoint.toEntity())
        }

        override fun get(id: String): Checkpoint? = dao.byId(id)?.toDomain()

        override fun forTask(taskId: String): List<Checkpoint> =
            dao.forTask(taskId).map { it.toDomain() }

        override fun latestForTask(taskId: String): Checkpoint? =
            dao.latestForTask(taskId)?.toDomain()

        override fun delete(id: String): Boolean = dao.deleteById(id) > 0

        override fun count(taskId: String?): Int =
            if (taskId == null) 0 else dao.countForTask(taskId)

        override fun clear() {
            dao.deleteAll()
        }
    }
}

internal fun Checkpoint.toEntity() = CheckpointEntity(
    id = id,
    taskId = taskId,
    label = label,
    createdAtMillis = createdAtMillis,
    workspaceId = workspaceId,
    payload = payload,
    parentId = parentId,
)