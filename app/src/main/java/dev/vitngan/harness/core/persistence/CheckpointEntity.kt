package dev.vitngan.harness.core.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Room row for a [Checkpoint]. */
@Entity(tableName = "checkpoints")
data class CheckpointEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "workspace_id") val workspaceId: String?,
    @ColumnInfo(name = "payload") val payload: String,
    @ColumnInfo(name = "parent_id") val parentId: String?,
) {
    fun toDomain(): Checkpoint = Checkpoint(
        id = id,
        taskId = taskId,
        label = label,
        createdAtMillis = createdAtMillis,
        workspaceId = workspaceId,
        payload = payload,
        parentId = parentId,
    )
}