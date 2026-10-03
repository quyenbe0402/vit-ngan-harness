package dev.vitngan.harness.core.hermes

/**
 * The five identifiers this system juggles, and which one is which.
 *
 * Confusing these is the single most likely source of a subtle, hard-to-debug
 * integration bug, so the mapping is explicit and typed rather than implied by
 * parameter names.
 *
 * ```
 *  Harness taskId      android task UUID            created by the Harness, per task
 *  Harness sessionId   android session UUID         created by the Harness, 1:1 with task
 *  Hermes sessionId    LIVE runtime session id      returned by session.create / session.resume
 *  Hermes storedId     DURABLE persisted session   returned by session.create; the id
 *                                                  session.resume expects back
 *  transport id        one live connection          dies on reconnect, reused never
 *  JSON-RPC id         one in-flight request        numeric or string, scoped to the connection
 * ```
 *
 * Upstream trap, quoted from contracts/sessions.py:
 * "`session_id` is the STORED id (or an exact title); the reply's `session_id`
 * is the runtime id."
 *
 * So `session.resume` takes the *stored* id as its `session_id` param and
 * returns a *runtime* `session_id`. Sending a runtime id to `session.resume` is
 * not guaranteed to work. [HermesSessionRef] keeps both apart so a caller
 * cannot confuse them.
 */
data class HermesSessionRef(
    /** Harness-side UUID. */
    val harnessSessionId: String,
    /** Harness task this session belongs to. */
    val taskId: String,
    /** Hermes live runtime session id. */
    val runtimeSessionId: String?,
    /** Hermes durable session id - what session.resume takes. */
    val storedSessionId: String?,
) {
    /**
     * The id to pass as `session_id` on methods that address an existing
     * session (interrupt, events.since, status, close).
     */
    val liveId: String?
        get() = runtimeSessionId ?: storedSessionId

    /** The id to pass to session.resume. Null until a durable row exists. */
    val resumeId: String? get() = storedSessionId

    /** The `session_id` every server->client request params carries. */
    val requestSessionId: String?
        get() = runtimeSessionId ?: storedSessionId

    fun withCreated(created: HermesSessionCreated): HermesSessionRef = copy(
        runtimeSessionId = created.sessionId,
        storedSessionId = created.storedSessionId,
    )

    fun closed(): HermesSessionRef = copy(runtimeSessionId = null)

    companion object {
        /**
         * Builds a ref from a raw session.create result.
         *
         * [harnessSessionId] and [taskId] are Harness-owned and are never
         * derived from the gateway's ids.
         */
        fun fromCreated(
            harnessSessionId: String,
            taskId: String,
            created: HermesSessionCreated,
        ) = HermesSessionRef(harnessSessionId, taskId, created.sessionId, created.storedSessionId)
    }
}

/**
 * Session lifecycle as the Harness observes it.
 *
 * The Harness tracks its own lifecycle; it does not attempt to mirror Hermes'
 * session_reaper, session_reclaimed or auto-continue behaviour.
 */
enum class HermesSessionState {
    CREATED,
    READY,
    CLOSED,
    FAILED,
}