package dev.vitngan.harness.core.persistence

import kotlinx.serialization.Serializable

/** A restorable point in agent execution. */
@Serializable
data class Checkpoint(
    val id: String,
    val taskId: String,
    val label: String,
    val createdAtMillis: Long,
    val workspaceId: String? = null,
    /** Serialised snapshot payload. Never trusted on restore. */
    val payload: String = "",
    val parentId: String? = null,
)