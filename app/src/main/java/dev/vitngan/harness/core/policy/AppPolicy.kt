package dev.vitngan.harness.core.policy

/**
 * A veto rule held in trusted policy.
 *
 * Deny rules are evaluated before any allow decision and **cannot be
 * overridden** by the agent. This is what makes trusted policy authoritative
 * over agent intent (S6, S8).
 */
data class DenyRule(
    val id: String,
    val capability: Capability?,
    /** When set, only paths under this prefix are denied. Null denies the whole capability. */
    val pathPrefix: String?,
    val reason: String,
) {
    init {
        require(id.isNotBlank()) { "DenyRule.id must not be blank" }
    }

    /**
     * True when this rule vetoes [capability] for a path under [candidatePath].
     *
     * A null [capability] matches every capability. A null [pathPrefix]
     * matches every path for its capability.
     *
     * Both sides are normalised before comparison, so a rule written as
     * `/secrets` still matches a request spelled `secrets/a.txt`. Without
     * this, a deny rule could be silently bypassed by omitting a leading
     * slash - which would turn the rule from a guarantee into a suggestion.
     */
    fun matches(capability: Capability, candidatePath: String?): Boolean {
        if (this.capability != null && this.capability != capability) return false
        val prefix = pathPrefix ?: return true
        val candidate = candidatePath ?: return false
        val p = normaliseForMatch(prefix)
        if (p.isEmpty() || p == "/") return true
        val c = normaliseForMatch(candidate)
        if (c == p) return true
        // Boundary-aware: "/ws/data" must not match "/ws/database".
        return c.startsWith("$p/")
    }

    private fun normaliseForMatch(value: String): String {
        var v = value.replace('\\', '/')
        while (v.contains("//")) v = v.replace("//", "/")
        if (v.length > 1 && v.endsWith("/")) v = v.dropLast(1)
        return if (v.startsWith("/")) v else "/$v"
    }
}

/**
 * Per-application trusted policy.
 *
 * Security invariant S6: this lives in trusted storage, **outside** the
 * agent-visible workspace. The agent can read its own workspace freely but
 * cannot reach this object, so it cannot widen its own privileges.
 */
data class AppPolicy(
    val packageName: String,
    val enabledCapabilities: Set<Capability>,
    val denyRules: List<DenyRule> = emptyList(),
    /** Capabilities that need an interactive user grant before use. */
    val userAuthRequired: Set<Capability> = emptySet(),
) {
    init {
        require(packageName.isNotBlank()) { "AppPolicy.packageName must not be blank" }
    }

    fun declaresEnabled(capability: Capability): Boolean = capability in enabledCapabilities

    /** First deny rule that vetoes this request, or null. */
    fun firstMatchingDenyRule(capability: Capability, candidatePath: String?): DenyRule? =
        denyRules.firstOrNull { it.matches(capability, candidatePath) }

    fun requiresUserAuth(capability: Capability): Boolean = capability in userAuthRequired

    companion object {
        /**
         * A deliberately minimal default: read and list only.
         *
         * Write, delete and process execution are absent by default so a newly
         * installed app cannot act until policy is deliberately widened.
         */
        fun readOnly(packageName: String): AppPolicy = AppPolicy(
            packageName = packageName,
            enabledCapabilities = setOf(
                Capability.WORKSPACE_READ,
                Capability.WORKSPACE_LIST,
                Capability.CONTEXT_READ,
            ),
        )
    }
}

/**
 * Evidence of an interactive user grant for a security-sensitive capability.
 *
 * Held in trusted storage. The agent can never construct one: only the
 * user-facing approval surface can, which is what keeps Hermes from
 * self-authorising (S8).
 */
data class UserAuth(
    val packageName: String,
    val capability: Capability,
    val grantedAtMillis: Long,
    /** When true the grant is one-shot and is consumed on first use. */
    val singleUse: Boolean = true,
    val consumed: Boolean = false,
) {
    fun isValid(nowMillis: Long): Boolean = !consumed && grantedAtMillis <= nowMillis
}