package dev.vitngan.harness.core.runtime

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Termux-backed Hermes runtime - **stub** for M0-002.
 *
 * Declares the contract and refuses to pretend it works. Nothing is launched;
 * [start] returns false and [health] stays IDLE, so no caller can mistake this
 * for a working runtime.
 */
class TermuxRuntimeBackend(
    private val clock: () -> Long = System::currentTimeMillis,
) : HermesRuntime {

    private val incoming = ConcurrentLinkedQueue<BridgeMessage>()
    private var state: RuntimeHealth = RuntimeHealth.IDLE

    override val name: String = "termux"

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

    override fun drainIncoming(): List<BridgeMessage> = incoming.toList().also { incoming.clear() }

    /** Helper for tests and future wiring. */
    internal fun emitForTest(message: BridgeMessage) {
        incoming += message.copy(timestampMillis = clock())
    }
}