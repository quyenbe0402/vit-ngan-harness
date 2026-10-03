package dev.vitngan.harness.core.runtime

import kotlinx.serialization.Serializable

/** Observable state of a runtime backend. */
@Serializable
enum class RuntimeHealth {
    /** Never started. */
    IDLE,

    /** Starting or handshaking. */
    STARTING,

    /** Usable. */
    READY,

    /** Started but not usable. */
    DEGRADED,

    /** Explicitly unsupported on this platform, by design. */
    UNSUPPORTED,

    /** Stopped after a failure. */
    FAILED,
}