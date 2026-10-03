package dev.vitngan.harness.core.hermes

import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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

        // A response frame: has "result" or "error" and no "method".
        if (root["method"] == null) {
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

        val method = root["method"].asString()
            ?: return HermesInbound.Ignored("method is not a string")

        val params = root["params"].asObject() ?: HermesJson.emptyObject()

        // id present => a server->client request that needs a correlated answer.
        // No id => a notification.
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