package dev.vitngan.harness.core.context

import kotlinx.serialization.Serializable

/**
 * A file offered to the agent as context.
 *
 * Untrusted content: this is workspace data the model will read, and reading it
 * grants nothing (S7).
 */
@Serializable
data class ContextFile(
    val path: String,
    val content: String,
    val sizeBytes: Int,
    val language: String? = null,
) {
    val lineCount: Int get() = content.count { it == '\n' } + if (content.isEmpty()) 0 else 1
}