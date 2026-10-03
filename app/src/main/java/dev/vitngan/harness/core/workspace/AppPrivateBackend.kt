package dev.vitngan.harness.core.workspace

import java.io.File

/**
 * The app-private storage backend.
 *
 * Security invariant S5: this is the **only** class permitted to use
 * `java.io.File` for workspace operations. Every other component works with
 * [CanonicalPath] and cannot obtain a [File].
 *
 * Even here, [CanonicalPath] is re-checked for containment before any I/O, so
 * a caller cannot use this class to reach outside the workspace root.
 */
class AppPrivateBackend(
    override val descriptor: WorkspaceDescriptor,
) : WorkspaceBackend {

    override fun contains(path: CanonicalPath): Boolean =
        path.rootPath == descriptor.rootPath && isInsideRoot(path.absolutePath)

    override fun list(path: CanonicalPath): BackendResult<List<FileMeta>> {
        if (!contains(path)) {
            return BackendResult.Failure("path is outside this workspace: ${path.absolutePath}")
        }
        val dir = File(path.absolutePath)
        if (!dir.isDirectory) {
            return BackendResult.Failure("not a directory: ${path.absolutePath}")
        }
        val entries = dir.listFiles()
            ?: return BackendResult.Failure("cannot list ${path.absolutePath}")
        return BackendResult.Ok(entries.sortedBy { it.name }.map { it.toMeta() })
    }

    override fun read(path: CanonicalPath): BackendResult<String> {
        if (!contains(path)) {
            return BackendResult.Failure("path is outside this workspace: ${path.absolutePath}")
        }
        val file = File(path.absolutePath)
        if (!file.isFile) {
            return BackendResult.Failure("not a regular file: ${path.absolutePath}")
        }
        if (file.length() > descriptor.maxFileBytes) {
            return BackendResult.Failure(
                "file exceeds the ${descriptor.maxFileBytes} byte limit (${file.length()} bytes)",
            )
        }
        return try {
            BackendResult.Ok(file.readText())
        } catch (e: Exception) {
            BackendResult.Failure("read failed: ${e.message}", e)
        }
    }

    override fun write(path: CanonicalPath, content: String): BackendResult<Unit> {
        if (descriptor.readOnly) {
            return BackendResult.Failure("workspace '${descriptor.id}' is read-only")
        }
        if (!contains(path)) {
            return BackendResult.Failure("path is outside this workspace: ${path.absolutePath}")
        }
        if (content.toByteArray().size > descriptor.maxFileBytes) {
            return BackendResult.Failure(
                "write would exceed the ${descriptor.maxFileBytes} byte limit",
            )
        }
        return try {
            val file = File(path.absolutePath)
            file.parentFile?.mkdirs()
            file.writeText(content)
            BackendResult.Ok(Unit)
        } catch (e: Exception) {
            BackendResult.Failure("write failed: ${e.message}", e)
        }
    }

    override fun delete(path: CanonicalPath): BackendResult<Unit> {
        if (descriptor.readOnly) {
            return BackendResult.Failure("workspace '${descriptor.id}' is read-only")
        }
        if (!contains(path)) {
            return BackendResult.Failure("path is outside this workspace: ${path.absolutePath}")
        }
        if (path.isRoot) {
            return BackendResult.Failure("refusing to delete the workspace root")
        }
        val file = File(path.absolutePath)
        if (!file.exists()) {
            return BackendResult.Failure("no such file: ${path.absolutePath}")
        }
        return try {
            val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
            if (deleted) BackendResult.Ok(Unit)
            else BackendResult.Failure("delete failed for ${path.absolutePath}")
        } catch (e: Exception) {
            BackendResult.Failure("delete failed: ${e.message}", e)
        }
    }

    /**
     * Separator-aware containment.
     *
     * The JVM unit tests run on Windows, where canonical paths use `\`, so a
     * `/`-only check would reject every legitimate path. Android is
     * `/`-only; both forms are handled so the rule is identical on either.
     */
    private fun isInsideRoot(candidate: String): Boolean {
        val root = descriptor.rootPath.trimEnd('/', '\\')
        return candidate == root ||
            candidate.startsWith("$root/") ||
            candidate.startsWith("$root\\")
    }

    private fun File.toMeta() = FileMeta(
        name = name,
        path = absolutePath,
        sizeBytes = if (isDirectory) 0L else length(),
        isDirectory = isDirectory,
        lastModifiedMillis = lastModified(),
        readable = canRead(),
        writable = canWrite(),
    )
}