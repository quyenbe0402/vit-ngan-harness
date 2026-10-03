package dev.vitngan.harness.core.policy

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks which capabilities have been requested or refused this session.
 *
 * This is deliberately **not** an authority. It records requests and defers to
 * [TrustedPolicyEngine]. A capability recorded here is not permission;
 * permission comes only from the engine returning [PolicyDecision.Allow] (S8).
 */
class CapabilityManager(
    private val policyEngine: TrustedPolicyEngine,
) {

    private val requested = ConcurrentHashMap.newKeySet<Capability>()
    private val denied = ConcurrentHashMap.newKeySet<Capability>()

    /** Records that [capability] was requested. Returns the trusted decision. */
    fun request(
        packageName: String,
        capability: Capability,
        candidatePath: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): PolicyDecision {
        requested += capability
        val decision = policyEngine.evaluate(packageName, capability, candidatePath, nowMillis)
        if (decision.isAllowed) denied -= capability else denied += capability
        return decision
    }

    /** True when the most recent request for [capability] was refused. */
    fun isDenied(capability: Capability): Boolean = capability in denied

    /** True when [capability] has ever been requested this session. */
    fun wasRequested(capability: Capability): Boolean = capability in requested

    /** True only when the trusted engine currently permits [capability]. */
    fun isGranted(
        packageName: String,
        capability: Capability,
        candidatePath: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean =
        policyEngine.evaluate(packageName, capability, candidatePath, nowMillis).isAllowed

    fun deniedCapabilities(): Set<Capability> = denied.toSet()

    fun requestedCapabilities(): Set<Capability> = requested.toSet()

    /** Clears request history. Grants remain a matter for the policy engine. */
    fun reset() {
        requested.clear()
        denied.clear()
    }
}