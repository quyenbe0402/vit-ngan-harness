package dev.vitngan.harness.core.runtime

/**
 * Embedded CPython runtime - **UNSUPPORTED experimental stub**.
 *
 * Declared so the capability is visible and so callers get an explicit
 * UNSUPPORTED health rather than a confusing failure later. It never starts,
 * never accepts frames, and never pretends to work.
 *
 * Embedding CPython on Android is not supported: the interpreter is not
 * available in the NDK, it cannot be shipped legally or reliably, and it
 * would bypass the Termux model the project has adopted.
 */
class EmbeddedPythonRuntimeBackend : HermesRuntime {

    override val name: String = "embedded-python"

    private val reason = "embedded CPython is not supported on Android"

    /** Why this backend is unsupported. Safe to surface in diagnostics. */
    fun unsupportedReason(): String = reason

    override fun isSupported(): Boolean = false

    override fun health(): RuntimeHealth = RuntimeHealth.UNSUPPORTED

    override fun start(): Boolean = false

    override fun stop() = Unit

    override fun send(message: BridgeMessage): Boolean = false

    override fun drainIncoming(): List<BridgeMessage> = emptyList()
}