package dev.vitngan.harness.core.workspace

import dev.vitngan.harness.core.policy.Capability
import dev.vitngan.harness.core.policy.CapabilityManager
import dev.vitngan.harness.core.policy.DenyCode
import dev.vitngan.harness.core.policy.PolicyDecision
import java.io.File

/** Outcome of a broker operation. */
sealed interface BrokerResult<out T> {

    /** The trusted engine permitted the action. */
    data class Ok<T>(val value: T, val decision: PolicyDecision.Allow) : BrokerResult<T>

    /** Trusted policy refused the action. */
    data class Denied(val decision: PolicyDecision.Deny) : BrokerResult<Nothing>

    /** Path defence refused, even though policy permitted the capability. */
    data class PathRefused(val resolution: PathResolution.Rejected) : BrokerResult<Nothing>

    /** The backend refused or failed. */
    data class BackendFailed(val reason: String) : BrokerResult<Nothing>
}

/**
 * The gatekeeper between policy, path defence, and storage.
 *
 * Security invariant S1: this is the only legitimate route from a capability
 * request to a filesystem operation. [resolve] refuses to return a
 * [CanonicalPath] unless trusted policy **and** [SecurityPathResolver] agree.
 */
class WorkspaceBroker(
    private val capabilityManager: CapabilityManager,
    private val pathResolver: SecurityPathResolver,
    private val backends: MutableMap<String, WorkspaceBackend> = LinkedHashMap(),
) {

    /** Registers a backend the app legitimately owns. */
    fun register(backend: WorkspaceBackend) {
        backends[backend.descriptor.id] = backend
    }

    fun descriptorFor(workspaceId: String): WorkspaceDescriptor? = backends[workspaceId]?.descriptor

    fun descriptors(): List<WorkspaceDescriptor> = backends.values.map { it.descriptor }

    /**
     * Resolves untrusted [rawPath] to an authorised [CanonicalPath].
     *
     * Both gates must pass. Policy is evaluated first, using the *unresolved*
     * path so a deny rule can veto on the raw text; the path is then resolved
     * and containment re-checked.
     */
    fun resolve(
        packageName: String,
        workspaceId: String,
        rawPath: String,
        capability: Capability,
        nowMillis: Long = System.currentTimeMillis(),
    ): BrokerResult<CanonicalPath> {
        val backend = backends[workspaceId]
            ?: return BrokerResult.PathRefused(
                PathResolution.Rejected(
                    "unknown workspace '$workspaceId'",
                    PathRejection.NOT_A_FILE,
                ),
            )

        val decision = capabilityManager.request(packageName, capability, rawPath, nowMillis)
        if (decision is PolicyDecision.Deny) return BrokerResult.Denied(decision)
        val allow = decision as PolicyDecision.Allow

        // A write/delete against a read-only descriptor is refused before any
        // path work, so a read-only workspace cannot be mutated even if the
        // capability were somehow granted.
        if (backend.descriptor.readOnly &&
            (capability == Capability.WORKSPACE_WRITE || capability == Capability.WORKSPACE_DELETE)
        ) {
            return BrokerResult.Denied(
                PolicyDecision.Deny(
                    capability = capability,
                    code = DenyCode.RULE_DENIED,
                    reason = "workspace '${backend.descriptor.id}' is read-only",
                ),
            )
        }

        val resolution = pathResolver.resolve(rawPath, File(backend.descriptor.rootPath))
        if (resolution is PathResolution.Rejected) {
            return BrokerResult.PathRefused(resolution)
        }
        val canonical = (resolution as PathResolution.Success).path

        // Final containment assertion at the broker boundary, independent of
        // the backend's own check. Defence in depth against a backend bug.
        if (!backend.contains(canonical)) {
            return BrokerResult.PathRefused(
                PathResolution.Rejected(
                    "resolved path is not contained by backend '${backend.descriptor.id}'",
                    PathRejection.ROOT_PREFIX_COLLISION,
                ),
            )
        }

        return BrokerResult.Ok(canonical, allow)
    }

    /**
     * Authorises [rawPath] for exactly one capability and returns the token.
     *
     * This is the only way to obtain an [AuthorisedPath], and it runs the
     * full chain: trusted policy first, then path defence. A caller cannot
     * mint a token, and cannot ask for a capability the policy will not grant.
     *
     * Used by the process path so that `ProcessManager` cannot be handed a
     * path that never went through policy (S1).
     */
    fun authorise(
        packageName: String,
        workspaceId: String,
        rawPath: String,
        capability: Capability,
        nowMillis: Long = System.currentTimeMillis(),
    ): AuthorisationResult = when (
        val resolved = resolve(packageName, workspaceId, rawPath, capability, nowMillis)
    ) {
        is BrokerResult.Ok -> AuthorisationResult.Granted(
            AuthorisedPath(
                path = resolved.value,
                capability = capability,
                reason = resolved.decision.reason,
            ),
        )
        is BrokerResult.Denied -> AuthorisationResult.Refused(resolved.decision)
        is BrokerResult.PathRefused -> AuthorisationResult.PathRejected(resolved.resolution)
        is BrokerResult.BackendFailed -> AuthorisationResult.PathRejected(
            PathResolution.Rejected(
                resolved.reason,
                PathRejection.IO_ERROR,
            ),
        )
    }
    /** Lists a directory after both gates pass. */
    fun list(
        packageName: String,
        workspaceId: String,
        rawPath: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): BrokerResult<List<FileMeta>> = withPath(
        packageName, workspaceId, rawPath, Capability.WORKSPACE_LIST, nowMillis,
    ) { backend, path -> backend.list(path) }

    /** Reads a file after both gates pass. */
    fun read(
        packageName: String,
        workspaceId: String,
        rawPath: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): BrokerResult<String> = withPath(
        packageName, workspaceId, rawPath, Capability.WORKSPACE_READ, nowMillis,
    ) { backend, path -> backend.read(path) }

    /** Writes a file after both gates pass. */
    fun write(
        packageName: String,
        workspaceId: String,
        rawPath: String,
        content: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): BrokerResult<Unit> = withPath(
        packageName, workspaceId, rawPath, Capability.WORKSPACE_WRITE, nowMillis,
    ) { backend, path -> backend.write(path, content) }

    /** Deletes a file after both gates pass. */
    fun delete(
        packageName: String,
        workspaceId: String,
        rawPath: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): BrokerResult<Unit> = withPath(
        packageName, workspaceId, rawPath, Capability.WORKSPACE_DELETE, nowMillis,
    ) { backend, path -> backend.delete(path) }

    private fun <T> withPath(
        packageName: String,
        workspaceId: String,
        rawPath: String,
        capability: Capability,
        nowMillis: Long,
        operation: (WorkspaceBackend, CanonicalPath) -> BackendResult<T>,
    ): BrokerResult<T> {
        val resolved = resolve(packageName, workspaceId, rawPath, capability, nowMillis)
        return when (resolved) {
            is BrokerResult.PathRefused -> resolved
            is BrokerResult.Denied -> resolved
            is BrokerResult.BackendFailed -> resolved
            is BrokerResult.Ok -> {
                val backend = backends.getValue(workspaceId)
                when (val outcome = operation(backend, resolved.value)) {
                    is BackendResult.Ok -> BrokerResult.Ok(outcome.value, resolved.decision)
                    is BackendResult.Failure -> BrokerResult.BackendFailed(outcome.reason)
                }
            }
        }
    }
}