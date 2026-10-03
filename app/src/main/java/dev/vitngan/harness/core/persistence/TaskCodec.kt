package dev.vitngan.harness.core.persistence

import dev.vitngan.harness.core.task.Task
import dev.vitngan.harness.core.task.TaskStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Wire form of a [Task]. */
@Serializable
data class TaskRecord(
    val id: String,
    val title: String,
    val description: String = "",
    val status: String,
    val workspaceId: String? = null,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val failureReason: String? = null,
)

/**
 * Serialises tasks for durable storage.
 *
 * Decoding is total: an unknown status or a malformed document yields an
 * empty list rather than an exception. Persisted data is untrusted input
 * (S7), and corrupt storage must not crash the caller that reads it.
 */
object TaskCodec : TaskStore.Codec {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val listSerializer = ListSerializer(TaskRecord.serializer())

    override fun encode(tasks: List<Task>): String =
        json.encodeToString(listSerializer, tasks.map { it.toRecord() })

    override fun fromText(text: String): List<Task> {
        if (text.isBlank()) return emptyList()
        return try {
            json.decodeFromString(listSerializer, text)
                .mapNotNull { it.toTaskOrNull() }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun Task.toRecord() = TaskRecord(
        id = id,
        title = title,
        description = description,
        status = status.name,
        workspaceId = workspaceId,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        failureReason = failureReason,
    )

    /** Returns null when the record cannot be represented safely. */
    private fun TaskRecord.toTaskOrNull(): Task? {
        if (id.isBlank()) return null
        val parsedStatus = TaskStatus.entries.firstOrNull { it.name == status } ?: return null
        return Task(
            id = id,
            title = title,
            description = description,
            status = parsedStatus,
            workspaceId = workspaceId,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
            failureReason = failureReason,
        )
    }
}