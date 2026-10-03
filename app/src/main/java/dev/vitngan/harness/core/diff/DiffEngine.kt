package dev.vitngan.harness.core.diff

/**
 * Computes a line diff.
 *
 * Deliberately free of any file access: it takes and returns text, so it can
 * never be used to read a path and needs no capability (S2).
 *
 * Uses a standard LCS backtrack so the result is deterministic and testable on
 * the JVM without a native diff library.
 */
class DiffEngine {

    /** Diffs two line lists and groups contiguous changes into hunks. */
    fun diff(original: List<String>, revised: List<String>): DiffResult {
        if (original == revised) return DiffResult()

        val lcs = longestCommonSubsequence(original, revised)
        if (lcs.isEmpty()) {
            // Nothing in common: every original line removed, every revised added.
            return DiffResult(
                listOf(
                    DiffHunk(
                        originalStart = 1,
                        originalLines = original.map { "-$it" },
                        revisedStart = 1,
                        revisedLines = revised.map { "+$it" },
                    ),
                ),
            )
        }

        val hunks = mutableListOf<DiffHunk>()
        var oi = 0
        var ri = 0
        var lcsPos = 0

        val pendingOrig = mutableListOf<String>()
        val pendingRev = mutableListOf<String>()
        var origStart = 1
        var revStart = 1

        // True while the current head of either input matches the next LCS item.
        fun atCommon(): Boolean =
            lcsPos < lcs.size &&
                oi < original.size && original[oi] == lcs[lcsPos] &&
                ri < revised.size && revised[ri] == lcs[lcsPos]

        fun flush() {
            if (pendingOrig.isNotEmpty() || pendingRev.isNotEmpty()) {
                hunks += DiffHunk(
                    originalStart = origStart,
                    originalLines = pendingOrig.toList(),
                    revisedStart = revStart,
                    revisedLines = pendingRev.toList(),
                )
                pendingOrig.clear()
                pendingRev.clear()
            }
        }

        while (oi < original.size || ri < revised.size) {
            // Copy common lines through untouched.
            while (atCommon()) {
                flush()
                oi++
                ri++
                lcsPos++
            }
            if (oi >= original.size && ri >= revised.size) break

            if (pendingOrig.isEmpty() && pendingRev.isEmpty()) {
                origStart = oi + 1
                revStart = ri + 1
            }

            // The original side stops as soon as it reaches the next common
            // line. It must be judged against the LCS on its own: comparing it
            // to the revised side too would consume unchanged lines whenever
            // the two inputs diverge at different offsets.
            while (oi < original.size &&
                !(lcsPos < lcs.size && original[oi] == lcs[lcsPos])
            ) {
                pendingOrig.add("-" + original[oi])
                oi++
            }
            while (ri < revised.size &&
                !(lcsPos < lcs.size && revised[ri] == lcs[lcsPos])
            ) {
                pendingRev.add("+" + revised[ri])
                ri++
            }
        }
        flush()

        return DiffResult(hunks)
    }

    /**
     * Splits text into lines.
     *
     * [String.lines] yields a trailing empty element for text ending in a
     * newline, which would appear as a phantom added/removed line and shift
     * every hunk position. Dropping it keeps counts and offsets faithful.
     */
    private fun toLines(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val lines = text.lines()
        return if (lines.isNotEmpty() && lines.last().isEmpty()) lines.dropLast(1) else lines
    }

    fun diffText(original: String, revised: String): DiffResult =
        diff(toLines(original), toLines(revised))

    /** Lines shared by both inputs, in order. */
    private fun longestCommonSubsequence(a: List<String>, b: List<String>): List<String> {
        if (a.isEmpty() || b.isEmpty()) return emptyList()
        val table = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) {
            for (j in b.indices.reversed()) {
                table[i][j] = if (a[i] == b[j]) table[i + 1][j + 1] + 1
                else maxOf(table[i + 1][j], table[i][j + 1])
            }
        }
        val out = ArrayList<String>()
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> { out.add(a[i]); i++; j++ }
                table[i + 1][j] >= table[i][j + 1] -> i++
                else -> j++
            }
        }
        return out
    }
}