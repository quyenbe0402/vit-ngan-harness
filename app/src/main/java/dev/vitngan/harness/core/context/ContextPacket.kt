package dev.vitngan.harness.core.context

import kotlinx.serialization.Serializable

/** A bounded bundle of context handed to the agent for one turn. */
@Serializable
data class ContextPacket(
    val files: List<ContextFile> = emptyList(),
    val truncated: Boolean = false,
    val totalBytes: Int = 0,
) {
    val fileCount: Int get() = files.size

    companion object {
        /** Default ceiling so a large repository cannot exhaust the window. */
        const val DEFAULT_MAX_BYTES: Int = 256 * 1024
    }
}