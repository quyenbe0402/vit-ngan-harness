package dev.vitngan.harness.core.runtime

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Termux-backed Hermes runtime - **stub**.
 *
 * M0-004 scope is deliberately limited. This backend:
 *  - does NOT call `Termux:API`
 *  - does NOT launch any external runtime
 *  - does NOT start a background process
 *  - does NOT shell out to anything
 *
 * It exists so the wiring, selection and failure paths are real and testable
 * before any integration is written. Every entry point refuses cleanly, so no
 * caller can mistake this for a working runtime.
 */
class TermuxRuntimeBackend(
    private val clock: () -> Long = System::currentTimeMillis,
) : HermesRuntime {

    private val incoming = ConcurrentLinkedQueue<BridgeMessage>()
    private var state: RuntimeHealth = RuntimeHealth.IDLE

    override val name: String = "termux"

    /** Termux integration is not present at M0-004. */
    override fun isSupported(): Boolean = false

    override fun health(): RuntimeHealth = state

    override fun start(): Boolean {
        state = RuntimeHealth.IDLE
        return false
    }

    override fun stop() {
        incoming.clear()
        state = RuntimeHealth.IDLE
    }

    /** Always refuses: an unauthorised runtime must not silently accept frames. */
    override fun send(message: BridgeMessage): Boolean = false

    override fun drainIncoming(): List<BridgeMessage> =
        incoming.toList().also { incoming.clear() }

    /** Test seam: inject a frame as if the runtime had produced it. */
    internal fun emitForTest(message: BridgeMessage) {
        incoming += message.copy(timestampMillis = clock())
    }
}