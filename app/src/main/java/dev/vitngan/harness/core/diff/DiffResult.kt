package dev.vitngan.harness.core.diff

import kotlinx.serialization.Serializable

/** The full difference between two texts. */
@Serializable
data class DiffResult(
    val hunks: List<DiffHunk> = emptyList(),
) {
    val isEmpty: Boolean get() = hunks.isEmpty()

    val addedLines: Int get() = hunks.sumOf { it.added }
    val removedLines: Int get() = hunks.sumOf { it.removed }

    /** Compact unified-style rendering, useful for reports. */
    fun render(): String = buildString {
        hunks.forEach { hunk ->
            hunk.originalLines.forEach { appendLine(it) }
            hunk.revisedLines.forEach { appendLine(it) }
        }
    }
}