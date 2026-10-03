package dev.vitngan.harness.core.hermes

import kotlinx.serialization.json.JsonObject

/**
 * Wire types for the Hermes tui_gateway JSON-RPC protocol.
 *
 * Audited from NousResearch/hermes-agent @ eaecc99c
 * (see docs/HERMES_UPSTREAM_AUDIT_M0-006.md).
 *
 * Only elements confirmed present in upstream source are modelled. Method and
 * event names are [String], never an enum: the gateway declares a closed set
 * server-side, but a client that crashes on an unrecognised name is worse than
 * one that ignores it.
 */
object HermesProtocol {
    const val JSONRPC_VERSION = "2.0"

    /** Methods M0-006 calls. Names are verbatim from upstream. */
    object Method {
        const val SESSION_CREATE = "session.create"
        const val SESSION_CLOSE = "session.close"
        const val SESSION_INTERRUPT = "session.interrupt"
        const val SESSION_STATUS = "session.status"
        const val SESSION_EVENTS_SINCE = "session.events.since"
        const val PROMPT_SUBMIT = "prompt.submit"

        /**
         * WebSocket-only liveness probe.
         *
         * Answered in `tui_gateway/ws.py` with `{"result": {"ok": true}}`. There
         * is **no** such handler in `tui_gateway/entry.py` (stdio); a live stdio
         * gateway returns `-32601 unknown method`. It must never be sent over
         * stdio.
         */
        const val GATEWAY_PING = "gateway.ping"
    }

    /**
     * Envelope method used by the real gateway for every notification.
     *
     * The event name is NOT here - it is in `params.type`. Verified against
     * `tui_gateway/entry.py` and `tui_gateway/ws.py`, which both emit
     * `{"jsonrpc":"2.0","method":"event","params":{"type":<name>,"payload":{...}}}`,
     * and `event_replay._stamp_event`, which returns early unless
     * `obj["method"] == "event"`. Confirmed against a live gateway in M0-007B.
     */
    const val METHOD_EVENT = "event"

    /** Events M0-006 consumes. Subset of the 60 declared upstream. */
    object Event {
        const val GATEWAY_READY = "gateway.ready"
        const val MESSAGE_START = "message.start"
        const val MESSAGE_DELTA = "message.delta"
        const val MESSAGE_INTERIM = "message.interim"
        const val MESSAGE_COMPLETE = "message.complete"
        const val TOOL_START = "tool.start"
        const val TOOL_COMPLETE = "tool.complete"
        const val TOOL_OUTPUT_RISK = "tool.output_risk"
        const val ERROR = "error"
        const val NOTICE = "notice"
        const val SESSION_INFO = "session.info"
        const val SESSION_TITLE = "session.title"
        const val SESSIONS_CHANGED = "sessions.changed"
        const val REQUEST_CANCEL = "request.cancel"
    }

    /**
     * Chain-of-thought streams. Present upstream and deliberately never mapped
     * into the Harness event bus - see HermesProtocolAdapter.coerceActivity.
     */
    object Private {
        const val REASONING_DELTA = "reasoning.delta"
        const val REASONING_AVAILABLE = "reasoning.available"
        const val THINKING_DELTA = "thinking.delta"
    }

    /**
     * Server->client request methods. These are genuine request/response, not
     * notifications: upstream routes an inbound response frame to
     * server_requests.resolve_response and emits no reply of its own.
     */
    object ServerRequest {
        const val CLARIFY = "clarify"
        const val APPROVAL = "approval"
        const val SUDO = "sudo"
        const val SECRET = "secret"
        const val VAULT_UNLOCK_PROMPT = "vault.unlock_prompt"
        const val VAULT_SAVE_LOGIN = "vault.save_login"
        const val VAULT_CODE = "vault.code"
    }

    /**
     * Server requests that seek credentials or a capability decision. The
     * bridge never answers these itself - they are escalated to policy.
     */
    val CREDENTIAL_REQUESTS = setOf(
        ServerRequest.SUDO,
        ServerRequest.SECRET,
        ServerRequest.VAULT_UNLOCK_PROMPT,
        ServerRequest.VAULT_SAVE_LOGIN,
        ServerRequest.VAULT_CODE,
    )

    /** Verified from rpc_dispatch.py / transport.py. */
    object ErrorCode {
        const val UNKNOWN_METHOD = -32601
        const val INTERNAL_ERROR = -32603
        const val SERVER_ERROR = -32000
        const val PARAMS_VIOLATION = 4000
        const val PROFILE_UNAVAILABLE = 4064
        const val BACKEND_RETIRING = 5035
    }
}

/** A JSON-RPC request or a server->client request (same shape upstream). */
data class HermesRequestFrame(
    val id: String,
    val method: String,
    val params: JsonObject,
)

/** A successful JSON-RPC response. */
data class HermesResponseFrame(
    val id: String,
    val result: JsonObject,
)

/** A JSON-RPC error response. The upstream error object is never discarded. */
data class HermesErrorFrame(
    val id: String?,
    val code: Int,
    val message: String,
    val data: JsonObject? = null,
)

/** One decoded inbound frame, discriminated by its own members. */
sealed class HermesInbound {
    data class Response(val frame: HermesResponseFrame) : HermesInbound()
    data class Error(val frame: HermesErrorFrame) : HermesInbound()

    /** A notification: `event` frames carrying `params.payload`. */
    data class Notification(val method: String, val payload: JsonObject) : HermesInbound()

    /** A server->client request awaiting a correlated answer. */
    data class ServerRequest(val frame: HermesRequestFrame) : HermesInbound()

    /** A frame with no `id` that is not an event - ignored, never fatal. */
    data class Ignored(val reason: String) : HermesInbound()
}

/** gateway.ready - the first frame of every connection. */
data class HermesGatewayReady(
    val replayEpoch: String,
    val changeEvents: Boolean,
    val heartbeat: Boolean?,
)

/** session.create result. Note the two distinct ids; see HermesSessionMapping. */
data class HermesSessionCreated(
    val sessionId: String,
    val storedSessionId: String,
    val messageCount: Int,
)

/** session.events.since result, used to recover after a reconnect. */
data class HermesReplayResult(
    val events: List<JsonObject>,
    val latestSeq: Long,
    val truncated: Boolean,
    val epoch: String,
)

/** session.interrupt result. Upstream InterruptStatus is a closed 2-value set. */
enum class HermesInterruptStatus { INTERRUPTED, NOT_INTERRUPTED, UNKNOWN }

data class HermesInterruptResult(
    val status: HermesInterruptStatus,
    val interrupted: Boolean?,
)