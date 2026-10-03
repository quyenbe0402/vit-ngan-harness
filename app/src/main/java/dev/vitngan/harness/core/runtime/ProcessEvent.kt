package dev.vitngan.harness.core.runtime

import kotlinx.serialization.Serializable

/** A lifecycle event from a supervised process. */
@Serializable
data class ProcessEvent(
    val processId: String,
    val kind: Kind,
    val detail: String = "",
    val timestampMillis: Long,
) {
    enum class Kind { STARTED, EXITED, STDOUT, STDERR, FAILED }
}