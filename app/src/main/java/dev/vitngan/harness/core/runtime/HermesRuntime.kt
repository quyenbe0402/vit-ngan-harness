package dev.vitngan.harness.core.runtime

/**
 * The Hermes runtime contract.
 *
 * M0-004 remains a **stub milestone**: Hermes is not rewritten, not vendored,
 * and no agent loop is reimplemented. The interface exists so the bridge has a
 * stable shape, and so a real backend can be added later without the rest of
 * the system changing.
 *
 * Security invariant S8: a runtime is a *transport*, never an authority. It
 * receives frames and reports health. It can never grant a capability, and
 * frames arriving from it are untrusted input that must be re-validated
 * upstream.
 */
interface HermesRuntime {

    /** Stable identifier, used for backend selection by [RuntimeManager]. */
    val name: String

    /** Whether this backend can run on the current platform at all. */
    fun isSupported(): Boolean

    /** Current observable state. Never throws. */
    fun health(): RuntimeHealth

    /**
     * Sends a frame to the runtime.
     *
     * Returns false rather than throwing when the runtime is unsupported or
     * not started. A stub must refuse, never pretend.
     */
    fun send(message: BridgeMessage): Boolean

    /** Begins startup. Returns false when unsupported. */
    fun start(): Boolean

    /** Stops the runtime. Safe to call when already stopped. */
    fun stop()

    /** Frames received from the runtime, oldest first, draining the buffer. */
    fun drainIncoming(): List<BridgeMessage>
}