package dev.vitngan.harness.core.persistence

import dev.vitngan.harness.core.task.Task

/**
 * Storage seam behind [dev.vitngan.harness.core.task.TaskManagerImpl].
 *
 * Kept separate so the task state machine stays unit-testable on the JVM and
 * so persistence can be swapped without touching the transitions.
 */
interface TaskStore {

    fun put(task: Task)

    fun get(id: String): Task?

    fun all(): List<Task>

    fun delete(id: String): Boolean

    fun clear()

    fun count(): Int

    /** Default in-memory store. */
    class InMemory : TaskStore {
        private val items = LinkedHashMap<String, Task>()

        @Synchronized override fun put(task: Task) {
            items[task.id] = task
        }

        @Synchronized override fun get(id: String): Task? = items[id]

        @Synchronized override fun all(): List<Task> =
            items.values.sortedBy { it.createdAtMillis }

        @Synchronized override fun delete(id: String): Boolean = items.remove(id) != null

        @Synchronized override fun clear() = items.clear()

        @Synchronized override fun count(): Int = items.size
    }

    /**
     * Serialisation seam for a durable store.
     *
     * Implementations must return an empty list rather than throwing on
     * malformed input: a corrupt row is untrusted data, and corrupt storage
     * must not take down the caller that reads it.
     */
    interface Codec {
        fun encode(tasks: List<Task>): String
        fun fromText(text: String): List<Task>
    }

    /** Text-backed store used for device persistence in the absence of Room. */
    class TextBacked(
        private var text: String,
        private val codec: Codec,
    ) : TaskStore {

        private fun read(): MutableMap<String, Task> =
            codec.fromText(text).associateBy { it.id }.toMutableMap()

        private fun write(items: Map<String, Task>) {
            text = codec.encode(items.values.sortedBy { it.createdAtMillis })
        }

        override fun put(task: Task) {
            val items = read()
            items[task.id] = task
            write(items)
        }

        override fun get(id: String): Task? = read()[id]

        override fun all(): List<Task> = read().values.sortedBy { it.createdAtMillis }

        override fun delete(id: String): Boolean {
            val items = read()
            val removed = items.remove(id) != null
            if (removed) write(items)
            return removed
        }

        override fun clear() {
            text = ""
        }

        override fun count(): Int = read().size

        /** The current serialised form, for persistence to a file or store. */
        fun serialised(): String = text
    }
}