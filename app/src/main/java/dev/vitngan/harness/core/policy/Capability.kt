package dev.vitngan.harness.core.policy

/**
 * A discrete privilege the agent can request.
 *
 * Capabilities are *requests*. They are never grants: a [TrustedPolicyEngine]
 * decides whether a requested [Capability] is permitted, and that decision is
 * what authorises action. See security invariant S8 - Hermes/model output can
 * name a capability but can never itself confer one.
 */
enum class Capability {
    WORKSPACE_READ,
    WORKSPACE_WRITE,
    WORKSPACE_DELETE,
    WORKSPACE_LIST,
    PROCESS_EXECUTE,
    CONTEXT_READ,
    CHECKPOINT_MANAGE,
    RUNTIME_INSPECT,
}

/** Stable, machine-readable denial codes. Stable strings keep logs comparable. */
enum class DenyCode {
    NOT_DECLARED,
    DISABLED,
    RULE_DENIED,
    USER_AUTH_REQUIRED,
    CHAIN_VIOLATION,
}

/**
 * The outcome of a policy evaluation.
 *
 * Modelled as a sealed hierarchy so callers cannot fabricate a permissive
 * result: the trusted engine is the only site that returns [Allow].
 */
sealed interface PolicyDecision {

    /** Human-readable reason, safe to surface in diagnostics. Never contains secrets. */
    val reason: String

    /** The request is permitted under trusted policy. */
    data class Allow(
        val capability: Capability,
        override val reason: String = "permitted by trusted policy",
    ) : PolicyDecision

    /** The request is refused. */
    data class Deny(
        val capability: Capability,
        val code: DenyCode,
        override val reason: String,
    ) : PolicyDecision

    val isAllowed: Boolean get() = this is Allow
}