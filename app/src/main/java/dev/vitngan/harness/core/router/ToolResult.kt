package dev.vitngan.harness.core.router

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Outcome of a routed tool call. */
@Serializable
sealed interface ToolResult {

    @Serializable
    @SerialName("success")
    data class Success(
        @SerialName("call_id") val callId: String,
        val output: String,
    ) : ToolResult

    @Serializable
    @SerialName("failure")
    data class Failure(
        @SerialName("call_id") val callId: String,
        val reason: String,
        val code: String,
    ) : ToolResult

    @Serializable
    @SerialName("denied")
    data class Denied(
        @SerialName("call_id") val callId: String,
        val reason: String,
        val code: String,
    ) : ToolResult

    val isSuccess: Boolean get() = this is Success
}