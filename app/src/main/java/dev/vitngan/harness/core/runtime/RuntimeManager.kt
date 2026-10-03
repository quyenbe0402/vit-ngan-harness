package dev.vitngan.harness.core.runtime

import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the set of Hermes runtimes and reports their health.
 *
 * A manager is not an authority on capability: it only decides *which* runtime
 * handles a frame. Whether the frame is permitted at all was already settled
 * upstream by policy (S8).
 */
class RuntimeManager(
    private val runtimes: MutableList<HermesRuntime> = mutableListOf(),
) {

    private val started = ConcurrentHashMap.newKeySet<String>()

    fun register(runtime: HermesRuntime) {
        synchronized(runtimes) { runtimes.add(runtime) }
    }

    fun all(): List<HermesRuntime> = synchronized(runtimes) { runtimes.toList() }

    fun byName(name: String): HermesRuntime? = all().firstOrNull { it.name == name }

    /** The first supported runtime, or null when none is usable. */
    fun firstSupported(): HermesRuntime? = all().firstOrNull { it.isSupported() }

    fun start(name: String): Boolean {
        val runtime = byName(name) ?: return false
        val ok = runtime.start()
        if (ok) started += name
        return ok
    }

    fun stop(name: String) {
        byName(name)?.stop()
        started -= name
    }

    fun stopAll() {
        all().forEach { it.stop() }
        started.clear()
    }

    fun healthSnapshot(): Map<String, RuntimeHealth> =
        all().associate { it.name to it.health() }

    fun isStarted(name: String): Boolean = name in started
}