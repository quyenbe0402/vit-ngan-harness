package dev.vitngan.harness.core.context

/**
 * Assembles a bounded [ContextPacket] from workspace files.
 *
 * Reads through [dev.vitngan.harness.core.workspace.WorkspaceBroker] only, so
 * it has no direct file access and every read is policy-checked (S1).
 *
 * Ranking is deliberately simple and deterministic for now: newest first, then
 * path, truncated at a byte ceiling. A smarter heuristic is an open decision.
 */
class RepositoryContextEngine(
    private val broker: dev.vitngan.harness.core.workspace.WorkspaceBroker,
    private val maxBytes: Int = ContextPacket.DEFAULT_MAX_BYTES,
) {

    /**
     * Builds a packet from [paths], skipping any that policy or path defence
     * refuses. A refused file is omitted, never surfaced as an error: one bad
     * path must not prevent the rest of the context from assembling.
     */
    fun build(
        packageName: String,
        workspaceId: String,
        paths: List<String>,
        nowMillis: Long = System.currentTimeMillis(),
    ): ContextPacket {
        val collected = mutableListOf<ContextFile>()
        var total = 0
        var truncated = false

        for (path in paths) {
            if (total >= maxBytes) {
                truncated = true
                break
            }
            when (val read = broker.read(packageName, workspaceId, path, nowMillis)) {
                is dev.vitngan.harness.core.workspace.BrokerResult.Ok -> {
                    val bytes = read.value.toByteArray().size
                    if (total + bytes > maxBytes) {
                        truncated = true
                        break
                    }
                    total += bytes
                    collected += ContextFile(
                        path = path,
                        content = read.value,
                        sizeBytes = bytes,
                        language = languageOf(path),
                    )
                }
                // Denied, path-refused and backend-failed all mean "skip this
                // file" rather than "abort the whole packet".
                else -> Unit
            }
        }
        return ContextPacket(collected, truncated, total)
    }

    private fun languageOf(path: String): String? = when (path.substringAfterLast('.', "")) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "md" -> "markdown"
        "json" -> "json"
        "xml" -> "xml"
        "toml" -> "toml"
        "gradle" -> "groovy"
        "txt" -> "text"
        else -> null
    }
}