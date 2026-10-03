package dev.vitngan.harness.core.hermes

import dev.vitngan.harness.core.runtime.BridgeMessage
import dev.vitngan.harness.core.runtime.HermesRuntime
import dev.vitngan.harness.core.runtime.RuntimeHealth

/**
 * A [HermesRuntime] backed by a [HermesBridge].
 *
 * This is the M0-004 contract adapter, not a runtime implementation: it owns no
 * process, starts no interpreter and ships no Hermes code. It exists so the
 * existing runtime seam can carry bridge frames once a real backend arrives.
 *
 * M0-006 ships **no** concrete backend. `TermuxHermesRuntime` and
 * `EmbeddedPythonHermesRuntime` are deliberately absent - the runtime layer stays
 * exactly as M0-004 left it, with its stubs still refusing.
 */
class BridgeBackedHermesRuntime(
    private val bridge: HermesBridge,
    override val name: String,
) : HermesRuntime {

    /** Outbound frames awaiting the caller to push them over a real transport. */
    private val outbox = ArrayDeque<String>()

    override fun isSupported(): Boolean = bridge.isReady

    override fun health(): RuntimeHealth = when {
        !bridge.isReady -> RuntimeHealth.IDLE
        bridge.isReady -> RuntimeHealth.READY
        else -> RuntimeHealth.DEGRADED
    }

    override fun send(message: BridgeMessage): Boolean {
        if (!bridge.isReady) return false
        outbox.addLast(message.content)
        return true
    }

    override fun start(): Boolean {
        if (!bridge.isReady) return false
        return true
    }

    override fun stop() {
        outbox.clear()
    }

    override fun drainIncoming(): List<BridgeMessage> {
        val adapter = HermesProtocolAdapter()
        val now = System.currentTimeMillis()
        return bridge.drainEvents().mapIndexedNotNull { index, event ->
            val activity = adapter.coerceActivity(event.method, event.payload) ?: return@mapIndexedNotNull null
            BridgeMessage(
                id = "$index-${event.method}",
                type = event.method,
                content = activity.toString(),
                timestampMillis = now,
            )
        }
    }

    fun drainOutbound(): List<String> = outbox.toList().also { outbox.clear() }
}
