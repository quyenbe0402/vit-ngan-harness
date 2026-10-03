package dev.vitngan.harness.core.runtime

/**
 * The Hermes runtime contract.
 *
 * M0-002 declares this as a **stub**. Hermes is not rewritten, not vendored,
 * and no agent loop is reimplemented here. The interface exists so the bridge
 * has a shape, and so an actual backend can be added later without the rest of
 * the system having to change.
 */
interface HermesRuntime {

    val name: String

    /** Whether this backend can run on the current platform at all. */
    fun isSupported(): Boolean

    /** Current observable state. */
    fun health(): RuntimeHealth

    /** Sends a frame to the runtime. Returns a rejection rather than throwing. */
    fun send(message: BridgeMessage): Boolean

    /** Begins startup. Returns false when unsupported. */
    fun start(): Boolean

    /** Stops the runtime. Safe to call when already stopped. */
    fun stop()

    /** Frames received from the runtime, in arrival order. */
    fun drainIncoming(): List<BridgeMessage>
}