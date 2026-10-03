package dev.vitngan.harness.core.workspace

import java.io.File

/** Why a path resolution was refused. Stable codes, comparable across logs. */
enum class PathRejection {
    EMPTY_PATH,
    TRAVERSAL_ESCAPE,
    ABSOLUTE_PATH_ESCAPE,
    ROOT_PREFIX_COLLISION,
    SYMLINK_ESCAPE,
    NOT_A_FILE,
    IO_ERROR,
}

/** Result of resolving an untrusted path string against a workspace root. */
sealed interface PathResolution {

    data class Success(val path: CanonicalPath) : PathResolution

    data class Rejected(val reason: String, val code: PathRejection) : PathResolution

    val isSuccess: Boolean get() = this is Success
}

/**
 * Indirection over the filesystem so path defence can be exercised in tests
 * and so the platform seam is explicit.
 */
interface FileSystemAccess {
    /** Canonical, symlink-resolved path. */
    fun canonicalPath(file: File): String

    /** Real production implementation. */
    object Default : FileSystemAccess {
        override fun canonicalPath(file: File): String = file.canonicalPath
    }
}