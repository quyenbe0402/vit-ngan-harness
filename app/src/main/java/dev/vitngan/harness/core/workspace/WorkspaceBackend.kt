package dev.vitngan.harness.core.workspace

import java.io.File

/** Storage kind behind a [WorkspaceBackend]. */
enum class WorkspaceBackendType {
    APP_PRIVATE,
    EXTERNAL_STORAGE,
    USER_SELECTED,
    REMOVABLE,
}

/**
 * Metadata about an authorised workspace.
 *
 * A descriptor is only ever produced for a directory the app has legitimately
 * claimed. The agent cannot mint one.
 */
data class WorkspaceDescriptor(
    val id: String,
    val displayName: String,
    val backendType: WorkspaceBackendType,
    /** Canonical root path. Never agent-supplied. */
    val rootPath: String,
    val readOnly: Boolean = false,
    val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
) {
    init {
        require(id.isNotBlank()) { "WorkspaceDescriptor.id must not be blank" }
        require(rootPath.isNotBlank()) { "WorkspaceDescriptor.rootPath must not be blank" }
        require(maxFileBytes > 0) { "maxFileBytes must be positive" }
    }

    companion object {
        /** 8 MiB. Guards against an agent pulling a huge file into memory. */
        const val DEFAULT_MAX_FILE_BYTES: Long = 8L * 1024 * 1024
    }
}

/** Metadata about a file, as reported by a backend. */
data class FileMeta(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val lastModifiedMillis: Long,
    val readable: Boolean,
    val writable: Boolean,
)

/** Outcome of a backend operation. */
sealed interface BackendResult<out T> {

    data class Ok<T>(val value: T) : BackendResult<T>

    data class Failure(val reason: String, val cause: Throwable? = null) : BackendResult<Nothing>
}

/**
 * A storage location the app may access.
 *
 * Backends are the **only** place permitted to touch `java.io.File` (S5).
 * Everything upstream works with [CanonicalPath] and never with raw files.
 */
interface WorkspaceBackend {

    val descriptor: WorkspaceDescriptor

    /** True when [path] lives inside this backend's root. */
    fun contains(path: CanonicalPath): Boolean

    fun list(path: CanonicalPath): BackendResult<List<FileMeta>>

    fun read(path: CanonicalPath): BackendResult<String>

    fun write(path: CanonicalPath, content: String): BackendResult<Unit>

    fun delete(path: CanonicalPath): BackendResult<Unit>
}