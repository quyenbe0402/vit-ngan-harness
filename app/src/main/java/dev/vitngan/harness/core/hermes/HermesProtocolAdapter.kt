package dev.vitngan.harness.core.hermes

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Everything that knows the Hermes wire shape.
 *
 * Above this layer nothing is JSON, and below it nothing is Hermes. The adapter
 * is also the enforcement point for the chain-of-thought filter, which is why
 * filtering lives here rather than in the event mapper: it must be impossible
 * to route a private stream by accident.
 */
class HermesProtocolAdapter {

    // ── outbound ─────────────────────────────────────────────────────────────

    fun sessionCreate(params: SessionCreateSpec): String {
        val obj = buildJsonObject {
            if (params.title != null) put("title", JsonPrimitive(params.title))
            if (params.model != null) put("model", JsonPrimitive(params.model))
            if (params.provider != null) put("provider", JsonPrimitive(params.provider))
            if (params.cwd != null) put("cwd", JsonPrimitive(params.cwd))
            if (params.cwdExplicit != null) put("cwd_explicit", JsonPrimitive(params.cwdExplicit))
            if (params.source != null) put("source", JsonPrimitive(params.source))
        }
        return HermesFraming.encodeRequest(NEXT_ID.getAndIncrement().toString(), HermesProtocol.Method.SESSION_CREATE, obj)
    }

    fun sessionClose(sessionId: String): String = sessionScoped(
        HermesProtocol.Method.SESSION_CLOSE,
        sessionId,
        buildJsonObject { put("session_id", JsonPrimitive(sessionId)) },
    )

    fun sessionInterrupt(sessionId: String, expectedHostedTaskId: String? = null): String {
        val obj = buildJsonObject {
            put("session_id", JsonPrimitive(sessionId))
            if (expectedHostedTaskId != null) put("expected_hosted_task_id", JsonPrimitive(expectedHostedTaskId))
        }
        return sessionScoped(HermesProtocol.Method.SESSION_INTERRUPT, sessionId, obj)
    }

    fun sessionEventsSince(sessionId: String, lastSeen: Long?): String {
        val obj = buildJsonObject {
            put("session_id", JsonPrimitive(sessionId))
            if (lastSeen != null) put("last_seen", JsonPrimitive(lastSeen))
        }
        return sessionScoped(HermesProtocol.Method.SESSION_EVENTS_SINCE, sessionId, obj)
    }

    fun promptSubmit(sessionId: String, text: String): String {
        val obj = buildJsonObject {
            put("session_id", JsonPrimitive(sessionId))
            put("prompt", JsonPrimitive(text))
        }
        return sessionScoped(HermesProtocol.Method.PROMPT_SUBMIT, sessionId, obj)
    }

    fun gatewayPing(): String = HermesFraming.encodeRequest(
        NEXT_ID.getAndIncrement().toString(),
        HermesProtocol.Method.GATEWAY_PING,
        HermesJson.emptyObject(),
    )

    /** The correlated answer to a server->client request. */
    fun serverRequestResult(requestId: String, result: JsonObject): String =
        HermesFraming.encodeResponse(requestId, result)

    // ── inbound ──────────────────────────────────────────────────────────────

    fun decode(line: String): HermesInbound = HermesFraming.decode(line)

    fun ready(payload: JsonObject): HermesGatewayReady? {
        val epoch = payload.stringOrNull("replay_epoch") ?: return null
        return HermesGatewayReady(
            replayEpoch = epoch,
            changeEvents = payload.booleanOrNull("change_events") ?: false,
            heartbeat = payload.booleanOrNull("heartbeat"),
        )
    }

    fun sessionCreated(result: JsonObject): HermesSessionCreated? {
        val live = result.stringOrNull("session_id") ?: return null
        val stored = result.stringOrNull("stored_session_id")
        return HermesSessionCreated(
            sessionId = live,
            storedSessionId = stored ?: live,
            messageCount = result.intOrNull("message_count") ?: 0,
        )
    }

    fun interruptResult(result: JsonObject): HermesInterruptResult {
        val status = when (result.stringOrNull("status")) {
            "interrupted" -> HermesInterruptStatus.INTERRUPTED
            "not_interrupted" -> HermesInterruptStatus.NOT_INTERRUPTED
            else -> HermesInterruptStatus.UNKNOWN
        }
        return HermesInterruptResult(status, result.booleanOrNull("interrupted"))
    }

    fun replayResult(result: JsonObject): HermesReplayResult {
        val events = (result["events"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { it as? JsonObject }
            .orEmpty()
        return HermesReplayResult(
            events = events,
            latestSeq = result.longOrNull("latest_seq") ?: 0L,
            truncated = result.booleanOrNull("truncated") ?: false,
            epoch = result.stringOrNull("epoch") ?: "",
        )
    }

    /**
     * True when [method] is a private chain-of-thought stream.
     *
     * Upstream emits reasoning.delta, reasoning.available, thinking.delta and a
     * `reasoning` field on message.complete. None may reach the Harness event
     * bus or the UI. Callers use this to *drop*, never to branch on content.
     */
    fun isPrivateStream(method: String): Boolean = method in setOf(
        HermesProtocol.Private.REASONING_DELTA,
        HermesProtocol.Private.REASONING_AVAILABLE,
        HermesProtocol.Private.THINKING_DELTA,
    )

    /**
     * Reduces an event payload to the safe, observable subset that may be
     * published toward the Harness.
     *
     * Returns null when nothing publishable survives - which is how the private
     * streams are discarded entirely.
     */
    fun coerceActivity(method: String, payload: JsonObject): Map<String, String>? {
        if (isPrivateStream(method)) return null

        return when (method) {
            HermesProtocol.Event.MESSAGE_DELTA ->
                payload.stringOrNull("text")?.let { mapOf("delta" to it) }

            HermesProtocol.Event.MESSAGE_START -> mapOf("phase" to "message_start")

            HermesProtocol.Event.MESSAGE_COMPLETE -> {
                // `reasoning` is deliberately not read.
                val text = (payload["text"] as? kotlinx.serialization.json.JsonPrimitive)
                    ?.takeIf { it.isString }?.content
                buildMap {
                    text?.let { put("text", it) }
                    payload.stringOrNull("status")?.let { put("status", it) }
                    payload.stringOrNull("warning")?.let { put("warning", it) }
                }.takeIf { it.isNotEmpty() }
            }

            HermesProtocol.Event.TOOL_START -> buildMap {
                payload.stringOrNull("tool_id")?.let { put("tool_id", it) }
                payload.stringOrNull("name")?.let { put("name", it) }
                payload.stringOrNull("context")?.let { put("context", it) }
            }.takeIf { it.isNotEmpty() }

            HermesProtocol.Event.TOOL_COMPLETE -> buildMap {
                payload.stringOrNull("tool_id")?.let { put("tool_id", it) }
                payload.stringOrNull("name")?.let { put("name", it) }
            }.takeIf { it.isNotEmpty() }

            HermesProtocol.Event.TOOL_OUTPUT_RISK ->
                payload.stringOrNull("name")?.let { mapOf("name" to it, "risk" to "flagged") }

            HermesProtocol.Event.ERROR ->
                payload.stringOrNull("message")?.let { mapOf("message" to it) }

            HermesProtocol.Event.NOTICE ->
                payload.stringOrNull("message")?.let { mapOf("message" to it) }

            HermesProtocol.Event.SESSION_TITLE ->
                payload.stringOrNull("title")?.let { mapOf("title" to it) }

            HermesProtocol.Event.SESSION_INFO -> mapOf("phase" to "session_info")

            HermesProtocol.Event.SESSIONS_CHANGED -> mapOf("phase" to "sessions_changed")

            // gateway.ready and request.cancel are handled by the bridge itself.
            else -> null
        }
    }

    private fun sessionScoped(method: String, sessionId: String, params: JsonObject): String =
        HermesFraming.encodeRequest(NEXT_ID.getAndIncrement().toString(), method, params)

    companion object {
        /**
         * Monotonic request ids, unique per process.
         *
         * Upstream accepts numeric or string ids and correlates only within a
         * connection, so a per-process counter is sufficient and a UUID would
         * only add noise.
         */
        private val NEXT_ID = java.util.concurrent.atomic.AtomicLong(1)
    }
}

/** Caller-supplied session.create fields. Null means "inherit". */
data class SessionCreateSpec(
    val title: String? = null,
    val model: String? = null,
    val provider: String? = null,
    /** Workspace path. Data only - re-validated through WorkspaceBroker by callers. */
    val cwd: String? = null,
    val cwdExplicit: Boolean? = null,
    val source: String? = null,
)