package dev.vitngan.harness.core.router

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A request from the agent to do something.
 *
 * A [ToolCall] is a *request*. It carries no authority: the router must take
 * it through capability, policy and path defence before anything executes
 * (S1, S8).
 */
@Serializable
data class ToolCall(
    val id: String,
    @SerialName("tool_name") val toolName: String,
    val arguments: Map<String, String> = emptyMap(),
    val packageName: String,
) {
    init {
        require(id.isNotBlank()) { "ToolCall.id must not be blank" }
        require(toolName.isNotBlank()) { "ToolCall.toolName must not be blank" }
        require(packageName.isNotBlank()) { "ToolCall.packageName must not be blank" }
    }

    fun arg(name: String): String? = arguments[name]
}