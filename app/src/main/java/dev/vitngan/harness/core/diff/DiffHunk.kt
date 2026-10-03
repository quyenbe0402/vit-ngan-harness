package dev.vitngan.harness.core.diff

import kotlinx.serialization.Serializable

/** One contiguous block of change. */
@Serializable
data class DiffHunk(
    val originalStart: Int,
    val originalLines: List<String>,
    val revisedStart: Int,
    val revisedLines: List<String>,
) {
    val added: Int get() = revisedLines.count { it.startsWith("+") }
    val removed: Int get() = originalLines.count { it.startsWith("-") }
}