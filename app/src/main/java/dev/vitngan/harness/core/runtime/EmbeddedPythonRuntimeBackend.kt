package dev.vitngan.harness.core.runtime

/**
 * Embedded CPython runtime - **UNSUPPORTED experimental stub**.
 *
 * This backend is declared so the capability is visible and so callers get an
 * explicit UNSUPPORTED health rather than a confusing failure later.
 *
 * It deliberately does NOT:
 *  - use Chaquopy or any embedded-Python toolchain
 *  - package a native Python runtime
 *  - start an interpreter
 *
 * Embedding CPython on Android is not viable: the interpreter is not provided
 * by the NDK, shipping it is not legally or reliably possible, and it would
 * bypass the Termux model this project has adopted. Declaring it as
 * unsupported is the honest outcome, not a gap to be filled later.
 */
@ExperimentalM0Runtime
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

/**
 * Marks a runtime surface as experimental and stub-only.
 *
 * Deliberately not `kotlin.ExperimentalStdlibApi`: this is a project-level
 * marker meaning "declared for the contract, not yet integrated", which is a
 * different and stronger claim than a stdlib stability warning.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "M0 runtime surface is a declared stub. It is not integrated with Hermes.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ExperimentalM0Runtime