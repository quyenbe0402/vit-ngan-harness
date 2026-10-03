package dev.vitngan.harness.core.workspace

/**
 * An opaque, already-authorised path.
 *
 * Security invariant S3 depends on this type: `ProcessManager` accepts a
 * [CanonicalPath] and never a raw `String`. Because construction is guarded,
 * a caller cannot hand an unchecked string to a process launch.
 *
 * Construction is `internal`, so only [SecurityPathResolver] - the component
 * that performs traversal defence - can mint one. Nothing that consumes
 * agent-supplied text may construct this type directly.
 */
class CanonicalPath internal constructor(
    /** Absolute, normalised, symlink-resolved path inside the workspace root. */
    val absolutePath: String,
    /** Workspace root this path was authorised against. */
    val rootPath: String,
) {

    /**
     * File name, or "/" for a filesystem root.
     *
     * Splits on both separators: Android is Unix-like, but the JVM on a
     * developer machine (and the JVM unit tests) run on Windows where the
     * canonical path uses `\`. Splitting only on `/` would return the whole
     * path here.
     */
    val fileName: String
        get() = absolutePath.substringAfterLast('/').substringAfterLast('\\')
            .ifEmpty { "/" }

    /** Extension without the dot, lower-cased, or empty when there is none. */
    val extension: String
        get() {
            val name = fileName
            val dot = name.lastIndexOf('.')
            return if (dot <= 0 || dot == name.length - 1) "" else name.substring(dot + 1).lowercase()
        }

    val isRoot: Boolean get() = absolutePath.trimEnd('/', '\\') == rootPath.trimEnd('/', '\\')

    /** True when [other] is this path or lives beneath it. */
    fun contains(other: CanonicalPath): Boolean {
        if (other.absolutePath == absolutePath) return true
        val p = absolutePath.trimEnd('/', '\\') + "/"
        val q = absolutePath.trimEnd('/', '\\') + "\\"
        return other.absolutePath.startsWith(p) || other.absolutePath.startsWith(q)
    }

    /**
     * The portion of the path relative to [rootPath], or "/" when at the root.
     * Returned with `/` separators so callers get one stable form regardless of
     * the host platform.
     */
    fun relativeToRoot(): String = when {
        isRoot -> "/"
        absolutePath.length > rootPath.length ->
            absolutePath.substring(rootPath.length)
                .trimStart('/', '\\')
                .replace('\\', '/')
                .ifEmpty { "/" }
        else -> "/"
    }

    override fun toString(): String = "CanonicalPath($absolutePath)"

    // Identity includes the root, so a path authorised for one workspace never
    // compares equal to the same text authorised for another.
    override fun equals(other: Any?): Boolean =
        other is CanonicalPath &&
            other.absolutePath == absolutePath &&
            other.rootPath == rootPath

    override fun hashCode(): Int = 31 * absolutePath.hashCode() + rootPath.hashCode()
}