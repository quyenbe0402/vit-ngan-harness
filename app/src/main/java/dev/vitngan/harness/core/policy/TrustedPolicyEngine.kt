package dev.vitngan.harness.core.policy

import java.util.concurrent.ConcurrentHashMap

/**
 * The trusted policy engine: the single place where "may this app do X?" is
 * answered.
 *
 * Security invariants enforced here:
 *  - S6 trusted policy is consulted, never derived from workspace content
 *  - S8 a request never grants itself; only this engine returns Allow
 *  - deny rules veto unconditionally
 *
 * This class deliberately holds no reference to any workspace content.
 */
class TrustedPolicyEngine(
    private val policyProvider: (String) -> AppPolicy?,
    private val userAuthStore: MutableMap<String, UserAuth> = ConcurrentHashMap(),
) {

    /** Records a user grant. Only a real approval surface should call this. */
    fun grantUserAuth(auth: UserAuth) {
        userAuthStore[authKey(auth.packageName, auth.capability)] = auth
    }

    /** Consumes a single-use grant, returning true when it was consumed. */
    fun consumeUserAuth(packageName: String, capability: Capability, nowMillis: Long): Boolean {
        val key = authKey(packageName, capability)
        val auth = userAuthStore[key] ?: return false
        if (!auth.isValid(nowMillis)) {
            userAuthStore.remove(key)
            return false
        }
        if (auth.singleUse) userAuthStore.remove(key)
        return true
    }

    /** Drops any stored grant for a package. Used on revoke/uninstall. */
    fun revoke(packageName: String) {
        userAuthStore.keys.removeAll { it.startsWith("$packageName:") }
    }

    /**
     * Evaluates a capability request.
     *
     * Order is fixed and callers cannot skip a step:
     *   1. unknown package      -> NOT_DECLARED
     *   2. capability disabled  -> DISABLED
     *   3. matching deny rule   -> RULE_DENIED
     *   4. user auth required   -> USER_AUTH_REQUIRED
     *   5. otherwise            -> Allow
     */
    fun evaluate(
        packageName: String,
        capability: Capability,
        candidatePath: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): PolicyDecision {
        val policy = policyProvider(packageName)
            ?: return PolicyDecision.Deny(
                capability = capability,
                code = DenyCode.NOT_DECLARED,
                reason = "no trusted policy for package '$packageName'",
            )

        // Defence in depth: a provider bug must not hand package A the policy
        // written for package B. The returned policy must name this package.
        if (policy.packageName != packageName) {
            return PolicyDecision.Deny(
                capability = capability,
                code = DenyCode.NOT_DECLARED,
                reason = "policy for '${policy.packageName}' does not apply to '$packageName'",
            )
        }

        if (!policy.declaresEnabled(capability)) {
            return PolicyDecision.Deny(
                capability = capability,
                code = DenyCode.DISABLED,
                reason = "capability ${capability.name} is not enabled for '$packageName'",
            )
        }

        policy.firstMatchingDenyRule(capability, candidatePath)?.let { rule ->
            return PolicyDecision.Deny(
                capability = capability,
                code = DenyCode.RULE_DENIED,
                reason = "denied by rule '${rule.id}': ${rule.reason}",
            )
        }

        if (policy.requiresUserAuth(capability)) {
            val consumed = consumeUserAuth(packageName, capability, nowMillis)
            if (!consumed) {
                return PolicyDecision.Deny(
                    capability = capability,
                    code = DenyCode.USER_AUTH_REQUIRED,
                    reason = "capability ${capability.name} requires an interactive user grant",
                )
            }
        }

        return PolicyDecision.Allow(
            capability = capability,
            reason = "permitted by trusted policy for '$packageName'",
        )
    }

    private fun authKey(packageName: String, capability: Capability) = "$packageName:${capability.name}"
}