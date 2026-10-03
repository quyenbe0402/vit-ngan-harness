package dev.vitngan.harness.core.router

import dev.vitngan.harness.core.policy.Capability
import dev.vitngan.harness.core.policy.CapabilityManager
import dev.vitngan.harness.core.policy.DenyCode
import dev.vitngan.harness.core.policy.PolicyDecision
import dev.vitngan.harness.core.workspace.BrokerResult
import dev.vitngan.harness.core.workspace.WorkspaceBroker

/**
 * Routes agent tool calls through the trusted chain.
 *
 * Security invariant S1: every workspace-touching route passes
 * [CapabilityManager] and [WorkspaceBroker] before any I/O. The router cannot
 * reach a backend directly.
 *
 * Ordering is deterministic: exact tool name beats prefix match, and an
 * unknown tool is refused rather than ignored.
 */
class ToolRouter(
    private val capabilityManager: CapabilityManager,
    private val workspaceBroker: WorkspaceBroker,
) {

    /** A handler for one tool. Receives the resolved, authorised path. */
    fun interface WorkspaceHandler {
        fun handle(call: ToolCall, rawPath: String): ToolResult
    }

    private data class Route(
        val capability: Capability,
        val argName: String,
        val handler: WorkspaceHandler,
    )

    private val exactRoutes = LinkedHashMap<String, Route>()
    private val prefixRoutes = LinkedHashMap<String, Route>()

    fun registerExact(toolName: String, capability: Capability, argName: String, handler: WorkspaceHandler) {
        require(toolName.isNotBlank()) { "toolName must not be blank" }
        exactRoutes[toolName] = Route(capability, argName, handler)
    }

    fun registerPrefix(prefix: String, capability: Capability, argName: String, handler: WorkspaceHandler) {
        require(prefix.isNotBlank()) { "prefix must not be blank" }
        prefixRoutes[prefix] = Route(capability, argName, handler)
    }

    fun registeredTools(): List<String> = exactRoutes.keys.toList()

    /** Routes [call]. Never throws for an unknown or refused call. */
    fun route(call: ToolCall, workspaceId: String, nowMillis: Long = System.currentTimeMillis()): ToolResult {
        val route = exactRoutes[call.toolName]
            ?: prefixRoutes.entries.firstOrNull { call.toolName.startsWith(it.key) }?.value
            ?: return ToolResult.Failure(
                call.id,
                "no handler is registered for tool '${call.toolName}'",
                "UNKNOWN_TOOL",
            )

        val rawPath = call.arg(route.argName)
            ?: return ToolResult.Failure(
                call.id,
                "tool '${call.toolName}' requires argument '${route.argName}'",
                "MISSING_ARGUMENT",
            )

        // Policy first (S1). A denial here never reaches the workspace.
        val decision = capabilityManager.request(call.packageName, route.capability, rawPath, nowMillis)
        if (decision is PolicyDecision.Deny) {
            return ToolResult.Denied(call.id, decision.reason, decision.code.name)
        }

        // Then path defence, then the backend.
        val resolved = workspaceBroker.resolve(
            packageName = call.packageName,
            workspaceId = workspaceId,
            rawPath = rawPath,
            capability = route.capability,
            nowMillis = nowMillis,
        )

        return when (resolved) {
            is BrokerResult.Denied ->
                ToolResult.Denied(call.id, resolved.decision.reason, resolved.decision.code.name)
            is BrokerResult.PathRefused ->
                ToolResult.Denied(
                    call.id,
                    resolved.resolution.reason,
                    "PATH_${resolved.resolution.code.name}",
                )
            is BrokerResult.BackendFailed ->
                ToolResult.Failure(call.id, resolved.reason, "BACKEND_ERROR")
            is BrokerResult.Ok -> runCatching { route.handler.handle(call, rawPath) }
                .getOrElse { ToolResult.Failure(call.id, "handler threw: ${it.message}", "HANDLER_ERROR") }
        }
    }

    /** Codes a caller can observe. Stable for tests and logs. */
    object Codes {
        const val UNKNOWN_TOOL = "UNKNOWN_TOOL"
        const val MISSING_ARGUMENT = "MISSING_ARGUMENT"
        const val BACKEND_ERROR = "BACKEND_ERROR"
        const val HANDLER_ERROR = "HANDLER_ERROR"
        val POLICY_DENY = DenyCode.RULE_DENIED.name
    }
}