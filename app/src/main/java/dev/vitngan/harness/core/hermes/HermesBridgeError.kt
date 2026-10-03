package dev.vitngan.harness.core.hermes

/**
 * Typed bridge failures.
 *
 * Every failure mode the audit identified has a case, and each keeps the
 * originating protocol detail so the upstream cause is never swallowed: a
 * [RemoteError] carries the gateway's own code and message, never a flattened
 * "request failed".
 */
sealed class HermesBridgeError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** Transport not started, already closed, or the peer is gone. */
    class TransportUnavailable(message: String = "hermes transport unavailable") :
        HermesBridgeError(message)

    /** Well-formed JSON that is not a frame we can route. */
    class MalformedMessage(message: String, cause: Throwable? = null) :
        HermesBridgeError(message, cause)

    /** The gateway rejected our params, method, or frame semantics. */
    class ProtocolError(
        val upstreamCode: Int,
        message: String,
    ) : HermesBridgeError(message)

    /** Referenced a session the gateway does not have. */
    class SessionNotFound(val sessionId: String) :
        HermesBridgeError("hermes session not found: $sessionId")

    /** No response within the deadline. */
    class RequestTimeout(val method: String, val timeoutMillis: Long) :
        HermesBridgeError("hermes request timed out: $method after ${timeoutMillis}ms")

    /** Cancelled locally, or withdrawn upstream via request.cancel. */
    class RequestCancelled(val method: String, val reason: String) :
        HermesBridgeError("hermes request cancelled: $method ($reason)")

    /** An error frame returned by the gateway. Upstream detail is retained. */
    class RemoteError(
        val upstreamCode: Int,
        override val message: String,
    ) : HermesBridgeError(message) {
        override fun toString() = "RemoteError(code=$upstreamCode, message=$message)"
    }

    /** Transport is connected but authentication was refused. */
    class AuthenticationFailure(message: String) : HermesBridgeError(message)

    /** A response arrived for a request id we never sent or already retired. */
    class OrphanResponse(val requestId: String, val detail: String) :
        HermesBridgeError("orphan hermes response id=$requestId ($detail)")

    companion object {
        /** Maps a JSON-RPC error code to a typed error, keeping the original. */
        fun fromUpstream(frame: HermesErrorFrame, method: String): HermesBridgeError = when (frame.code) {
            HermesProtocol.ErrorCode.UNKNOWN_METHOD -> ProtocolError(
                frame.code,
                "unknown hermes method '$method' - client and gateway versions differ: ${frame.message}",
            )

            HermesProtocol.ErrorCode.PARAMS_VIOLATION -> ProtocolError(
                frame.code,
                "params rejected by the hermes contract for '$method': ${frame.message}",
            )

            else -> RemoteError(frame.code, frame.message)
        }
    }
}