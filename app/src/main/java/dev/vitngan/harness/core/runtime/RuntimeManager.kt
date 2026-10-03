package dev.vitngan.harness.core.runtime

/**
 * Owns the set of Hermes runtimes and reports their health.
 *
 * Backend switching is **safe by construction**: every operation is total.
 * Selecting an unknown or unsupported backend yields false or null rather
 * than an exception, because runtime selection happens during startup and a
 * crash there takes the whole harness down for a condition the user cannot
 * act on.
 *
 * A manager is not an authority on capability. It only decides *which*
 * runtime handles a frame; whether the frame is permitted was already settled
 * upstream by policy (S8).
 */
class RuntimeManager(
    private val runtimes: MutableList<HermesRuntime> = mutableListOf(),
) {

    private var activeName: String? = null

    fun register(runtime: HermesRuntime) {
        synchronized(runtimes) {
            // Registering the same name twice replaces, so a reconfigured
            // harness does not end up with two indistinguishable backends.
            runtimes.removeAll { it.name == runtime.name }
            runtimes.add(runtime)
        }
    }

    fun all(): List<HermesRuntime> = synchronized(runtimes) { runtimes.toList() }

    fun byName(name: String): HermesRuntime? = all().firstOrNull { it.name == name }

    /** The first supported runtime, or null when none is usable. */
    fun firstSupported(): HermesRuntime? = all().firstOrNull { it.isSupported() }

    /** Name of the runtime currently selected, or null. */
    fun activeBackend(): String? = activeName

    /**
     * Selects [name] and starts it.
     *
     * Returns false - never throws - when the backend is unknown or reports
     * itself unsupported. A previous selection is left untouched on failure
     * so a bad switch cannot clear a working backend.
     */
    fun start(name: String): Boolean {
        val runtime = byName(name) ?: return false
        if (!runtime.isSupported()) return false
        if (!runtime.start()) return false
        activeName = name
        return true
    }

    /**
     * Switches to [name] without assuming success.
     *
     * The previous backend is stopped only once the new one has actually
     * started, so a failed switch leaves a running system intact.
     */
    fun switchTo(name: String): Boolean {
        val previous = activeName
        if (previous == name) return isStarted(name)
        if (!start(name)) return false
        previous?.let { byName(it)?.stop() }
        return true
    }

    fun stop(name: String) {
        byName(name)?.stop()
        if (activeName == name) activeName = null
    }

    fun stopAll() {
        all().forEach { it.stop() }
        activeName = null
    }

    /** Health of every registered backend, by name. Never throws. */
    fun healthSnapshot(): Map<String, RuntimeHealth> =
        all().associate { it.name to runCatching { it.health() }.getOrDefault(RuntimeHealth.FAILED) }

    fun isStarted(name: String): Boolean = activeName == name
}