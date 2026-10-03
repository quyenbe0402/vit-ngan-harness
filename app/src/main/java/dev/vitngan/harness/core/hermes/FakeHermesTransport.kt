package dev.vitngan.harness.core.hermes

import kotlinx.coroutines.channels.Channel

/**
 * In-memory [HermesTransport] for tests.
 *
 * Frames written by the bridge are captured verbatim and can be inspected, so a
 * test can assert on the exact wire bytes - which is the only way to prove the
 * adapter emits protocol-accurate JSON rather than merely plausible JSON.
 */
class FakeHermesTransport(
    /** When false, [send] refuses exactly like a peer that has gone away. */
    private val acceptSends: Boolean = true,
) : HermesTransport {

    /** Every frame the bridge has written, in order. */
    val sentFrames: MutableList<String> = mutableListOf()

    override val incoming: Channel<String> = Channel(Channel.UNLIMITED)

    private var open = true

    override val isOpen: Boolean get() = open && acceptSends

    override fun send(frame: String): Boolean {
        if (!isOpen) return false
        sentFrames.add(frame)
        return true
    }

    override fun close() {
        open = false
        incoming.close()
    }

    /** Simulates the peer vanishing mid-turn. */
    fun kill() {
        open = false
    }

    /** Delivers a frame to the bridge as if the gateway had sent it. */
    fun emit(frame: String) {
        incoming.trySend(frame)
    }

    /** Delivers raw text, including deliberately malformed text. */
    fun emitRaw(line: String) = emit(line)

    /** The most recently written frame. */
    fun lastSent(): String? = sentFrames.lastOrNull()

    fun sentCount(): Int = sentFrames.size

    /** True when any written frame contains [needle] as text. */
    fun anySentContaining(needle: String): Boolean = sentFrames.any { it.contains(needle) }

    companion object {
        /** A gateway.ready notification frame, as written by upstream. */
        fun readyFrame(replayEpoch: String = "epoch-1"): String =
            """{"jsonrpc":"2.0","method":"gateway.ready","params":{"payload":""" +
                """{"skin":{"name":"default"},"change_events":true,"replay_epoch":"$replayEpoch"}}}"""
    }
}
