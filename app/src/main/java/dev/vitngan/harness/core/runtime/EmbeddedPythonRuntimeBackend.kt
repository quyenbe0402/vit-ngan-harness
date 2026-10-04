package dev.vitngan.harness.core.runtime

/**
 * Embedded CPython runtime - **UNSUPPORTED by product decision**.
 *
 * This backend is declared so the capability is visible and so callers get an
 * explicit UNSUPPORTED health rather than a confusing failure later.
 *
 * It deliberately does NOT:
 *  - use Chaquopy or any embedded-Python toolchain
 *  - package a native Python runtime
 *  - start an interpreter
 *
 * CORRECTION (2026-10-04): the KDoc here previously read "Embedding CPython on
 * Android is not viable: the interpreter is not provided by the NDK, shipping it
 * is not legally or reliably possible..." That was **false**. M0-008L-G/M/O built
 * CPython 3.14 for Android arm64 and executed five Hermes native dependencies on
 * the physical device. The accurate statement is that embedded CPython is
 * **technically achievable and was demonstrated**, but is not the selected
 * runtime because the project pivoted to remote Hermes
 * (docs/PIVOT-DECISION.md).
 *
 * The class itself remains a stub: `isSupported()` returns false and no process
 * is started. That is now a product decision rather than a technical limitation,
 * and the distinction is recorded because a reader who assumes it is a technical
 * limitation will draw the wrong conclusion about Version-B.
 */
@ExperimentalM0Runtime
class EmbeddedPythonRuntimeBackend : HermesRuntime {

    override val name: String = "embedded-python"

    // User-facing diagnostic. Corrected 2026-10-04: the previous text
    // ("embedded CPython is not supported on Android") asserted a technical
    // impossibility that M0-008L-G/M/O disproved. The accurate statement is
    // that it is not the selected runtime.
    private val reason =
        "embedded CPython is not the selected runtime; see docs/PIVOT-DECISION.md. " +
            "It was built and proven on-device during M0-008L-G/M/O and is preserved " +
            "as Version-B, so this is a product decision rather than a limitation."

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