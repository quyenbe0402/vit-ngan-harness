package dev.vitngan.harness.core.hermes

import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Which upstream gateway transport this client speaks.
 *
 * The two are not interchangeable: upstream ships genuinely different method
 * catalogs on each, so health and readiness cannot be one universal notion.
 */
enum class HermesTransportKind {
    /** `tui_gateway.entry` over the process' stdin/stdout. */
    STDIO,

    /** `tui_gateway.ws` over a WebSocket. */
    WEBSOCKET,
}

/**
 * How a transport proves the peer is actually up.
 *
 * This exists because a single "health probe" was wrong: M0-006 sent
 * `gateway.ping` to every transport, and a real stdio gateway answers
 * `-32601 unknown method` because that handler only exists in `ws.py`.
 *
 * Each case names the upstream evidence it rests on, so a future reader can
 * re-verify rather than take it on faith.
 */
sealed class TransportHealthStrategy {
    /**
     * Readiness is the observed `gateway.ready` event; no probe is sent.
     *
     * Correct for stdio, where no ping method exists. `gateway.ready` is emitted
     * as the first frame by `tui_gateway/entry.py`, and has the identical shape
     * on the WebSocket path, so it is the one readiness signal valid everywhere.
     */
    data object ReadyEventOnly : TransportHealthStrategy()

    /**
     * Readiness additionally uses `gateway.ping`, verified in `tui_gateway/ws.py`.
     *
     * Kept for the WebSocket path only. Not available on stdio.
     */
    data object PingProbe : TransportHealthStrategy()

    /** True when this strategy writes a probe frame during connect. */
    val sendsProbe: Boolean
        get() = this is PingProbe
}

/**
 * Frame transport for the Hermes bridge.
 *
 * Deliberately the narrowest possible surface: it moves already-encoded frames
 * and nothing else. It knows nothing about JSON-RPC, sessions or policy, so a
 * stdio pipe, a WebSocket and an in-memory fake are interchangeable.
 *
 * Upstream framing is newline-delimited JSON (tui_gateway/transport.py:
 * serialize_frame + "\n"). [FramedHermesTransport] owns that detail so no other
 * layer has to.
 */
interface HermesTransport {

    /** Which upstream gateway transport this is. Drives readiness semantics. */
    val kind: HermesTransportKind

    /**
     * How this transport proves readiness.
     *
     * Must be derived from what upstream actually supports on [kind] - see
     * [TransportHealthStrategy].
     */
    val healthStrategy: TransportHealthStrategy
        get() = when (kind) {
            // Conservative default: never send a probe that may not exist.
            HermesTransportKind.STDIO -> TransportHealthStrategy.ReadyEventOnly
            HermesTransportKind.WEBSOCKET -> TransportHealthStrategy.PingProbe
        }

    /** False when the peer is gone, matching upstream Transport.write. */
    fun send(frame: String): Boolean

    /** Closes the transport. Safe to call more than once. */
    fun close()

    val isOpen: Boolean

    /** Frames arriving from the gateway. Closed when the transport dies. */
    val incoming: Channel<String>

    companion object {
        /**
         * Newline framing, as written by upstream `StdioTransport.write`.
         * Serialisation happens here so callers cannot emit a frame the
         * gateway would mis-split.
         */
        const val DELIMITER = "\n"
    }
}

/**
 * Splits and joins newline-delimited JSON frames.
 *
 * Kept separate from [HermesTransport] so framing is testable without a
 * transport and so a future WebSocket implementation can share it.
 */
object HermesFraming {

    /**
     * Decodes one physical line into an inbound frame.
     *
     * Returns [HermesInbound.Ignored] for anything unrecognisable. A malformed
     * frame must never throw into the reader loop: a single bad byte from the
     * gateway must not take down the connection or the app.
     */
    fun decode(line: String): HermesInbound {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return HermesInbound.Ignored("blank frame")

        val root = try {
            HermesJson.parseObject(trimmed)
        } catch (e: Exception) {
            return HermesInbound.Ignored("malformed json: ${e.message}")
        }

        // Upstream ids are strings or numbers; both correlate.
        val id = root["id"].asRawString()

        val method = root["method"].asString()
        val params = root["params"].asObject() ?: HermesJson.emptyObject()

        // A response frame: no method, and carries "result" or "error".
        if (method == null) {
            val error = root["error"].asObject()
            if (error != null) {
                return HermesInbound.Error(
                    HermesErrorFrame(
                        id = id,
                        code = error.intOrNull("code") ?: HermesProtocol.ErrorCode.SERVER_ERROR,
                        message = error.stringOrNull("message") ?: "unspecified protocol error",
                        data = error["data"].asObject(),
                    ),
                )
            }
            val result = root["result"].asObject()
            if (result != null && id != null) {
                return HermesInbound.Response(HermesResponseFrame(id, result))
            }
            return HermesInbound.Ignored("response frame without result or id")
        }

        // The real gateway wraps every notification as method="event" and puts
        // the event name in params.type (verified against tui_gateway
        // event_replay._stamp_event and against a live gateway in M0-007B).
        // Treating "event" as the event name would route every notification to a
        // method literally named "event", so it is unwrapped here.
        if (method == HermesProtocol.METHOD_EVENT) {
            if (id != null) return HermesInbound.Ignored("event frame must not carry an id")
            val eventName = params.stringOrNull("type")
                ?: return HermesInbound.Ignored("event frame without params.type")
            return HermesInbound.Notification(eventName, payloadOf(params))
        }

        // id present => a server->client request that needs a correlated answer.
        // No id => a notification named by its method.
        return if (id != null) {
            HermesInbound.ServerRequest(HermesRequestFrame(id, method, params))
        } else {
            HermesInbound.Notification(method, payloadOf(params))
        }
    }

    /**
     * Upstream event frames carry `params.payload`. Tolerates a payload that is
     * absent or malformed rather than dropping the whole notification.
     */
    private fun payloadOf(params: JsonObject): JsonObject =
        params["payload"].asObject() ?: HermesJson.emptyObject()

    fun encodeRequest(id: String, method: String, params: JsonObject): String {
        val obj = HermesJson.buildObject {
            put("jsonrpc", JsonPrimitive(HermesProtocol.JSONRPC_VERSION))
            put("id", JsonPrimitive(id))
            put("method", JsonPrimitive(method))
            put("params", params)
        }
        return HermesJson.encodeToString(obj)
    }

    fun encodeResponse(id: String, result: JsonObject): String {
        val obj = HermesJson.buildObject {
            put("jsonrpc", JsonPrimitive(HermesProtocol.JSONRPC_VERSION))
            put("id", JsonPrimitive(id))
            put("result", result)
        }
        return HermesJson.encodeToString(obj)
    }

    fun encodeError(id: String?, code: Int, message: String): String {
        val obj = HermesJson.buildObject {
            put("jsonrpc", JsonPrimitive(HermesProtocol.JSONRPC_VERSION))
            put("id", id?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
            put(
                "error",
                HermesJson.buildObject {
                    put("code", JsonPrimitive(code))
                    put("message", JsonPrimitive(message))
                },
            )
        }
        return HermesJson.encodeToString(obj)
    }
}