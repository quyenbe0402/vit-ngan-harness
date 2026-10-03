package dev.vitngan.harness.core.workspace

import dev.vitngan.harness.core.policy.Capability
import dev.vitngan.harness.core.policy.PolicyDecision

/**
 * A path that trusted policy has approved **for one specific capability**.
 *
 * Construction is `internal`, so the only place that can mint one is
 * [WorkspaceBroker], which mints it only after policy and path defence both
 * agree. That is what makes invariant S1 structural rather than advisory:
 * `ProcessManager` accepts an [AuthorisedPath], and a bare [CanonicalPath]
 * obtained some other way cannot be passed to it at all.
 *
 * @property path the approved path
 * @property capability the capability it was approved for
 * @property reason the policy decision that authorised it, for diagnostics
 */
class AuthorisedPath internal constructor(
    val path: CanonicalPath,
    val capability: Capability,
    val reason: String,
) {
    /** True only when this token was approved for process execution. */
    val allowsExecute: Boolean get() = capability == Capability.PROCESS_EXECUTE

    /** True only when this token was approved for reading. */
    val allowsRead: Boolean get() = capability == Capability.WORKSPACE_READ

    override fun toString(): String =
        "AuthorisedPath(${path.absolutePath}, $capability)"
}

/** Broker results that carry an authorisation token. */
sealed interface AuthorisationResult {

    data class Granted(val authorised: AuthorisedPath) : AuthorisationResult

    data class Refused(val decision: PolicyDecision.Deny) : AuthorisationResult

    data class PathRejected(val resolution: PathResolution.Rejected) : AuthorisationResult
}