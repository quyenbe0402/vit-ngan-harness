package dev.vitngan.harness.core.hermes

import kotlinx.serialization.json.JsonObject

/**
 * One in-flight Harness->gateway request.
 *
 * Upstream drops an answer whose id it does not recognise, so a lost or late
 * response is not a protocol violation - but it must still not corrupt the
 * Harness's own view. Every pending request therefore carries its session, its
 * deadline, its cancellation state and its settled outcome.
 */
class PendingRequest(
    val requestId: String,
    val method: String,
    val sessionId: String?,
    val createdAtMillis: Long,
    val timeoutMillis: Long,
) {
    @Volatile
    var cancelled: Boolean = false
        private set

    @Volatile
    var settled: Result<JsonObject>? = null
        private set

    val isSettled: Boolean get() = settled != null || cancelled

    fun isExpired(nowMillis: Long): Boolean = nowMillis - createdAtMillis > timeoutMillis

    fun expire(): Result<JsonObject> =
        Result.failure(HermesBridgeError.RequestTimeout(method, timeoutMillis))

    fun cancel(reason: String): Result<JsonObject> {
        cancelled = true
        return Result.failure(HermesBridgeError.RequestCancelled(method, reason))
    }

    fun resolve(value: JsonObject) {
        settled = Result.success(value)
    }

    fun fail(error: Throwable) {
        settled = Result.failure(error)
    }

    /**
     * The outcome if this request is polled now.
     *
     * Null means "still in flight" - the caller should keep waiting rather than
     * invent a result.
     */
    fun poll(nowMillis: Long): Result<JsonObject>? = when {
        settled != null -> settled
        cancelled -> Result.failure(HermesBridgeError.RequestCancelled(method, "cancelled by harness"))
        isExpired(nowMillis) -> expire().also { settled = it }
        else -> null
    }
}

/**
 * One server->client request the gateway is waiting on.
 *
 * Upstream routes an inbound response frame for these to
 * `server_requests.resolve_response` and emits no reply of its own, so they are
 * genuine request/response and must not be modelled as notifications.
 */
class PendingServerRequest(
    val requestId: String,
    val method: String,
    val sessionId: String?,
    val createdAtMillis: Long,
    val timeoutMillis: Long,
    val onSettled: (Result<JsonObject>) -> Unit = {},
) {
    @Volatile
    var cancelled: Boolean = false
        private set

    @Volatile
    var settled: Result<JsonObject>? = null
        private set

    fun isExpired(nowMillis: Long): Boolean = nowMillis - createdAtMillis > timeoutMillis

    fun settle(result: Result<JsonObject>) {
        settled = result
        onSettled(result)
    }

    fun withdraw(reason: String) {
        cancelled = true
        settle(Result.failure(HermesBridgeError.RequestCancelled(method, reason)))
    }
}

/**
 * Connection state as the bridge sees it.
 *
 * `WAITING_FOR_READY` is a real, non-optional step: a live transport is not the
 * same thing as a live Hermes. Only the observed `gateway.ready` event moves
 * the bridge to [READY].
 */
enum class HermesBridgeState {
    DISCONNECTED,
    CONNECTING,

    /** Transport is up; nothing has proved Hermes is. */
    WAITING_FOR_READY,

    /** The gateway's own `gateway.ready` event was observed. */
    READY,
}


/**
 * The bridge between the Harness and the Hermes gateway.
 *
 * ```
 * HermesRuntime -> HermesBridge -> HermesProtocolAdapter -> HermesTransport -> gateway
 * ```
 *
 * Responsibilities, and nothing else:
 *  - correlate JSON-RPC request ids to callers
 *  - enforce per-request timeouts
 *  - hold server->client requests and correlate their answers
 *  - survive malformed frames, unknown events and duplicate/late responses
 *  - expose gateway readiness and replay watermarks for reconnect
 *
 * Deliberately synchronous and clock-injected: every timing rule (timeout,
 * cancellation, expiry) is then provable in a plain JVM test with no sleeps and
 * no flakiness. A later runtime wraps it in a coroutine scope.
 *
 * Security invariant S8: a frame from Hermes is DATA and a REQUEST. Nothing
 * arriving here grants a capability. The bridge holds no CapabilityManager and
 * performs no authorisation; tool execution continues to route through
 * ToolRouter -> CapabilityManager -> TrustedPolicyEngine -> WorkspaceBroker /
 * ProcessManager. Credential-bearing server requests (sudo, secret, vault.*)
 * are surfaced as pending and are never auto-answered here.
 */
class HermesBridge(
    private val transport: HermesTransport,
    private val adapter: HermesProtocolAdapter = HermesProtocolAdapter(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val defaultTimeoutMillis: Long = 30_000L,
) {

    private val lock = Any()

    private val pending = LinkedHashMap<String, PendingRequest>()

    /** Settled requests, retained briefly so a late poll still gets an answer. */
    private val settled = LinkedHashMap<String, Result<JsonObject>>()
    private val serverRequests = LinkedHashMap<String, PendingServerRequest>()
    private val sessions = LinkedHashMap<String, HermesSessionRef>()
    private val buffered = ArrayDeque<HermesInbound.Notification>()

    private var watermark: Long = 0
    private var epoch: String? = null

    var state: HermesBridgeState = HermesBridgeState.DISCONNECTED
        private set

    var ready: HermesGatewayReady? = null
        private set

    /** Frames the bridge refused to route, for diagnostics. Never thrown. */
    var ignoredFrameCount: Int = 0
        private set

    val isReady: Boolean get() = state == HermesBridgeState.READY

    // ── lifecycle ────────────────────────────────────────────────────────────

    /**
     * Brings the transport up and begins waiting for the gateway.
     *
     * Readiness is **transport semantic**. On stdio this writes nothing at all:
     * upstream has no `gateway.ping` handler in `tui_gateway/entry.py`, so
     * probing with it produces `-32601 unknown method` on a real gateway. The
     * proof of readiness is the gateway's own `gateway.ready` event, which
     * [accept] promotes to [HermesBridgeState.READY].
     *
     * A live transport is deliberately not treated as a ready Hermes: the
     * bridge stays in [HermesBridgeState.WAITING_FOR_READY] until the gateway
     * says so.
     */
    fun connect() {
        if (!transport.isOpen) throw HermesBridgeError.TransportUnavailable()
        state = HermesBridgeState.CONNECTING

        // Only transports whose upstream actually implements a probe may send
        // one. STDIO resolves to ReadyEventOnly and therefore sends nothing.
        if (transport.healthStrategy.sendsProbe) {
            transport.send(adapter.gatewayPing())
        }

        state = HermesBridgeState.WAITING_FOR_READY
    }

    /** Which readiness contract this bridge is honouring. */
    fun healthStrategy(): TransportHealthStrategy = transport.healthStrategy

    /** The upstream transport this bridge speaks. */
    fun transportKind(): HermesTransportKind = transport.kind

    fun disconnect() {
        synchronized(lock) {
            pending.values.forEach { it.fail(HermesBridgeError.TransportUnavailable()) }
            pending.clear()
            serverRequests.values.forEach {
                it.settle(Result.failure(HermesBridgeError.TransportUnavailable()))
            }
            serverRequests.clear()
            buffered.clear()
            ready = null
            state = HermesBridgeState.DISCONNECTED
        }
        transport.close()
    }

    /** Drops connection-scoped state so a fresh connection can be established. */
    fun resetConnectionState() = synchronized(lock) {
        pending.clear()
        serverRequests.clear()
        buffered.clear()
        ready = null
        state = HermesBridgeState.DISCONNECTED
    }

    // ── Harness -> gateway ───────────────────────────────────────────────────

    /**
     * Registers [requestId] as in-flight and writes [frame].
     *
     * Returns false rather than throwing when the peer is gone, matching
     * upstream `Transport.write`.
     */
    fun send(
        frame: String,
        requestId: String,
        method: String,
        sessionId: String? = null,
        timeoutMillis: Long = defaultTimeoutMillis,
    ): Boolean {
        if (!transport.isOpen) throw HermesBridgeError.TransportUnavailable()
        val sent = transport.send(frame)
        if (!sent) return false
        synchronized(lock) {
            pending[requestId] = PendingRequest(
                requestId = requestId,
                method = method,
                sessionId = sessionId,
                createdAtMillis = clock(),
                timeoutMillis = timeoutMillis,
            )
        }
        return true
    }

    /**
     * The in-flight outcome, or null when still pending.
     *
     * Settled requests are retained here rather than removed on arrival, so a
     * caller that polls slightly late still receives the real answer instead of
     * a spurious null. [drainSettled] releases them.
     */
    fun await(requestId: String): Result<JsonObject>? {
        val request = synchronized(lock) { pending[requestId] } ?: return null
        val outcome = request.poll(clock())
        if (outcome != null) synchronized(lock) { settled[requestId] = outcome }
        return outcome
    }

    /** True while the request has neither a response nor a deadline. */
    fun isPending(requestId: String): Boolean = synchronized(lock) {
        val request = pending[requestId] ?: return@synchronized false
        request.poll(clock()) == null
    }

    fun pendingCount(): Int = synchronized(lock) {
        pending.count { it.value.poll(clock()) == null }
    }

    /** Releases settled requests and returns their outcomes. */
    fun drainSettled(): Map<String, Result<JsonObject>> = synchronized(lock) {
        val out = LinkedHashMap(settled)
        settled.clear()
        out
    }

    /** Cancels an in-flight request; the caller is told, not left hanging. */
    fun cancelRequest(requestId: String): Boolean {
        val request = synchronized(lock) { pending.remove(requestId) } ?: return false
        val outcome = request.cancel("cancelled by harness")
        synchronized(lock) { settled[requestId] = outcome }
        return true
    }

    // ── gateway -> Harness ───────────────────────────────────────────────────

    /**
     * Routes one inbound frame.
     *
     * Never throws. A malformed frame, an unknown event, a duplicate response
     * and a late response are all absorbed, so version skew degrades instead of
     * crashing the app.
     */
    fun accept(line: String) {
        when (val inbound = adapter.decode(line)) {
            is HermesInbound.Ignored -> synchronized(lock) { ignoredFrameCount++ }
            is HermesInbound.Error -> onError(inbound.frame)
            is HermesInbound.Response -> onResponse(inbound.frame)
            is HermesInbound.Notification -> onNotification(inbound)
            is HermesInbound.ServerRequest -> onServerRequest(inbound.frame)
        }
    }

    private fun onResponse(frame: HermesResponseFrame) {
        val request = synchronized(lock) { pending[frame.id] }
        // Unknown id: a duplicate or a late answer. Upstream would drop it too.
        if (request == null) return
        // Already answered: a second response for the same id must not overwrite
        // the result the caller already received.
        if (request.settled != null) return
        request.resolve(frame.result)
        synchronized(lock) { settled[frame.id] = Result.success(frame.result) }
    }

    private fun onError(frame: HermesErrorFrame) {
        val id = frame.id ?: return
        val request = synchronized(lock) { pending[id] } ?: return
        if (request.settled != null) return
        val error = HermesBridgeError.fromUpstream(frame, request.method)
        request.fail(error)
        synchronized(lock) { settled[id] = Result.failure(error) }
    }

    private fun onNotification(inbound: HermesInbound.Notification) {
        when (inbound.method) {
            HermesProtocol.Event.GATEWAY_READY -> {
                val parsed = adapter.ready(inbound.payload) ?: return
                synchronized(lock) {
                    // A new epoch means the gateway restarted, so the old
                    // watermark is meaningless and replay starts from scratch.
                    if (epoch != null && epoch != parsed.replayEpoch) watermark = 0
                    epoch = parsed.replayEpoch
                    ready = parsed
                    state = HermesBridgeState.READY
                }
                return
            }

            HermesProtocol.Event.REQUEST_CANCEL -> {
                val id = inbound.payload.stringOrNull("id") ?: return
                val method = inbound.payload.stringOrNull("method") ?: "unknown"
                val reason = inbound.payload.stringOrNull("reason") ?: "withdrawn"
                withdrawServerRequest(id, "$method withdrawn: $reason")
                return
            }
        }

        // Chain-of-thought is dropped before it can be buffered or forwarded.
        if (adapter.isPrivateStream(inbound.method)) return

        synchronized(lock) { buffered.addLast(inbound) }
    }

    private fun onServerRequest(frame: HermesRequestFrame) {
        synchronized(lock) {
            serverRequests[frame.id] = PendingServerRequest(
                requestId = frame.id,
                method = frame.method,
                sessionId = frame.params.stringOrNull("session_id"),
                createdAtMillis = clock(),
                timeoutMillis = defaultTimeoutMillis,
            )
        }
    }

    // ── server->client requests ──────────────────────────────────────────────

    fun serverRequestCount(): Int = synchronized(lock) { serverRequests.size }

    fun pendingServerRequest(requestId: String): PendingServerRequest? =
        synchronized(lock) { serverRequests[requestId] }

    fun pendingServerRequests(): List<PendingServerRequest> =
        synchronized(lock) { serverRequests.values.toList() }

    /**
     * Answers a server->client request.
     *
     * Credential-bearing methods are refused outright: the bridge will not
     * manufacture a password, and it will not silently pick an approval choice
     * on the user's behalf. That decision belongs to the policy layer.
     */
    fun answerServerRequest(requestId: String, result: JsonObject): Result<Unit> {
        val request = synchronized(lock) { serverRequests[requestId] }
            ?: return Result.failure(
                HermesBridgeError.OrphanResponse(requestId, "no pending server request with that id"),
            )

        if (request.method in HermesProtocol.CREDENTIAL_REQUESTS) {
            // Left in place so the policy layer can still see and route it.
            return Result.failure(
                HermesBridgeError.AuthenticationFailure(
                    "hermes requested credentials via '${request.method}'; the bridge will not answer it",
                ),
            )
        }

        val sent = transport.send(adapter.serverRequestResult(requestId, result))
        if (!sent) {
            return Result.failure(HermesBridgeError.TransportUnavailable("could not deliver the answer"))
        }
        synchronized(lock) { serverRequests.remove(requestId) }
        request.settle(Result.success(result))
        return Result.success(Unit)
    }

    /** Withdraws a held server request, mirroring upstream request.cancel. */
    fun withdrawServerRequest(requestId: String, reason: String): Boolean {
        val request = synchronized(lock) { serverRequests.remove(requestId) } ?: return false
        request.withdraw(reason)
        return true
    }

    /** Expires server requests the Harness never answered. */
    fun expireStaleServerRequests(): Int {
        val now = clock()
        val stale = synchronized(lock) {
            serverRequests.values.filter { it.isExpired(now) }.map { it.requestId }
        }
        stale.forEach { withdrawServerRequest(it, "timeout") }
        return stale.size
    }

    // ── event stream ─────────────────────────────────────────────────────────

    /** Drains buffered notifications oldest first. */
    fun drainEvents(): List<HermesInbound.Notification> = synchronized(lock) {
        val out = buffered.toList()
        buffered.clear()
        out
    }

    fun pendingEvents(): Int = synchronized(lock) { buffered.size }

    // ── sessions ─────────────────────────────────────────────────────────────

    fun registerSession(ref: HermesSessionRef) = synchronized(lock) { sessions[ref.harnessSessionId] = ref }

    fun session(harnessSessionId: String): HermesSessionRef? = synchronized(lock) { sessions[harnessSessionId] }

    fun updateSession(harnessSessionId: String, block: (HermesSessionRef) -> HermesSessionRef) {
        synchronized(lock) { sessions[harnessSessionId]?.let { sessions[harnessSessionId] = block(it) } }
    }

    fun forgetSession(harnessSessionId: String) = synchronized(lock) { sessions.remove(harnessSessionId) }

    fun sessionCount(): Int = synchronized(lock) { sessions.size }

    // ── reconnect ────────────────────────────────────────────────────────────

    fun replayWatermark(): Long = synchronized(lock) { watermark }

    fun recordWatermark(seq: Long) = synchronized(lock) { if (seq > watermark) watermark = seq }

    fun replayEpoch(): String? = synchronized(lock) { epoch }
}
