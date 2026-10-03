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
    override val kind: HermesTransportKind = HermesTransportKind.STDIO,
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
        /**
         * The real `gateway.ready` frame captured from a live Hermes stdio
         * gateway during M0-007B (`docs/M0-007B_TERMUX_REAL_WORLD_SPIKE.md`).
         *
         * Faithful to the observed wire shape, which differs from what M0-006
         * originally assumed in three ways:
         *
         *  1. The envelope method is the literal `"event"`. The event name is
         *     **not** there - it lives in `params.type`.
         *  2. `params` carries `type` *and* `payload`; the payload alone is not
         *     the whole frame.
         *  3. `skin` is a full theme object (10 keys, 28 colours), not just a
         *     name.
         *
         * `heartbeat` is deliberately absent: it is emitted on the WebSocket
         * path (`tui_gateway/ws.py`) but not on stdio (`tui_gateway/entry.py`),
         * so adding it would misrepresent this transport.
         */
        fun readyFrame(replayEpoch: String = "epoch-1"): String =
            """
            {"jsonrpc": "2.0", "method": "event", "params": {"type": "gateway.ready",
             "payload": {"skin": {"name": "default", "colors": {"banner_border": "#CD7F32",
             "banner_title": "#FFD700", "ui_accent": "#FFBF00", "ui_ok": "#4caf50",
             "ui_error": "#ef5350", "ui_warn": "#ffa726"}, "light_colors": {"banner_title": "#C8961E",
             "ui_accent": "#D89B04", "ui_ok": "#2E7D32"}, "dark_colors": {},
             "branding": {"agent_name": "Hermes Agent",
             "welcome": "Welcome to Hermes Agent! Type your message or /help for commands.",
             "goodbye": "Goodbye! ☤", "response_label": " ☤ Hermes ", "prompt_symbol": "❯",
             "help_header": "(^_^)? Available Commands"}, "banner_logo": "", "banner_hero": "",
             "tool_prefix": "┊", "help_header": "(^_^)? Available Commands", "customCSS": ""},
             "change_events": true, "replay_epoch": "$replayEpoch"}}}
            """.trimIndent().replace("\n", "")
    }
}
