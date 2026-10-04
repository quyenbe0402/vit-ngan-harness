# Hermes Bridge Specification

**This is the implementation contract for M21 Phase A.**

Everything below was read from the pinned Hermes source. Where a statement is
inferred rather than quoted, it is labelled. Nothing here is invented: no
`task.submit`, no `tool.start` client protocol, no second agent loop.

| Field | Value |
|---|---|
| Hermes repository | `https://github.com/NousResearch/hermes-agent` |
| Pinned commit | `eaecc99c7ec5b6f37e880a0b69d16871cd3e4f57` |
| Commit subject | `fix(discord): upsert recreated slash commands without a delete-first step (#104399)` |
| Local read path | `C:\dev\hermes-agent` (detached HEAD at the pinned commit) |
| Source files read | `tui_gateway/ws.py`, `transport.py`, `rpc_dispatch.py`, `contracts/base.py`, `contracts/registry.py`, `contracts/sessions.py`, `contracts/events.py`, `contracts/server_requests.py`, `contracts/prompt_voice.py` |
| Related | `docs/PIVOT-DECISION.md` (frozen architecture), `docs/OPEN-DECISIONS.md`, `docs/HERMES_UPSTREAM_AUDIT_M0-006.md` |

---

## 0. Erratum to M0-006

**`gateway.ping` is not a contract method.** M0-006 cited it as if it were
discoverable in the contract catalog. Verified false: no contract for
`gateway.ping` exists in `contracts/*.py`. It is answered **inline in the
WebSocket read loop, before dispatch** (`ws.py:420-424`).

Consequence for the client: `gateway.ping` is a **transport liveness primitive**,
handled outside the request/response envelope and outside Pydantic validation.
It must never travel the `session.*` / `prompt.*` path.

Recorded here rather than edited into M0-006. History is not rewritten.

---

## 1. Two distinct frame shapes

This is the single most important structural fact, and conflating the two is how
a client silently corrupts the stream.

### 1.1 Request/response frames (client â†’ server)

```json
{"jsonrpc":"2.0","id":<n>,"method":"session.create","params":{...}}
```

The reply carries the **same `id`** and either `result` or `error`.

### 1.2 Event frames (server â†’ client, unsolicited)

```json
{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{...}}}
```

**The event name lives in `params.type`, not in `method`.** `method` is always the
literal string `"event"`. Verified `ws.py:365-370`.

A client that switches on `method` will never see a single event type.

### 1.3 Serverâ†’client request frames (server asks the client)

Structurally a request frame from the server's side: `method` is the request name
(`clarify`, `approval`, ...), plus an `id` the client must echo in its response.
There is **no `method:"event"`** here.

**Therefore `method` alone does not disambiguate direction.** The client must
track which ids it issued (its own requests) versus which ids arrived
server-initiated. Verified `server_requests.py:1-6`, `rpc_dispatch.py:53-58`.

---

## 2. Framing

`ws.py:1-4` docstring and `transport.py`:

- Framing is **newline-delimited JSON**, identical to stdio.
- `serialize_frame` = `json.dumps(obj, ensure_ascii=False)`.
- `StdioTransport.write` appends `"\n"`.

**On WebSocket the newline is implicit** - each WS text message is one complete
JSON frame. The client must **not** split on newlines, and must not attempt to
reassemble across messages. `ensure_ascii=False` means **UTF-8 text arrives
un-escaped**; the client must handle non-ASCII payloads directly.

Sanitisation note: the server strips lone surrogates before sending
(`ws.py:48-57`, `_sanitize_ws_text`, issue #97288), because an unescaped
surrogate previously latched a whole connection closed.

---

## 3. Connection lifecycle

Ordered, from `ws.py:352-394`:

1. `ws.accept()` (optionally with a subprotocol negotiated by the mounting host)
2. `_note_dashboard_client_activity(force=True)` - scale-to-zero liveness marker
3. `_disable_nagle(ws)`
4. `skin_payload = await asyncio.to_thread(server.resolve_skin)`
5. **`gateway.ready` is written immediately** - before any client RPC
6. background starters (`_start_backend_heartbeat_refresher`, orphan sweep)
7. dispatcher task created; read loop begins

If the ready frame cannot be sent the connection is **closed immediately** with
`disconnect_reason = "ready_send_failed"` (`ws.py:388-392`).

**Client obligation:** a connection that has not delivered `gateway.ready` is
not usable. Do not send RPCs before it. Treat a connection that closes during
handshake as a failed attempt, not a protocol error.

---

## 4. `gateway.ready`

Written at `ws.py:365-370`:

```json
{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{
  "skin": <theme object>,
  "change_events": true,
  "heartbeat": true,
  "replay_epoch": <string>
}}}
```

| Field | Meaning |
|---|---|
| `skin` | full theme object, **not a name** (10 keys / 28 colours observed at M0-007B) |
| `change_events` | this backend broadcasts changes; clients may demote legacy polls to backstops |
| `heartbeat` | **a flag**, not an event stream. See section 5 |
| `replay_epoch` | changes when the **backend restarts**. Used to reset per-session seq watermarks (`ws.py:363-364`) |

**Client obligation:** record `replay_epoch`. On reconnect, if it differs from the
stored value, **discard stored seq watermarks and refetch**. A gateway restart
invalidates incremental resume.
---

## 5. Heartbeat / ping

**`heartbeat: true` is a flag inside `gateway.ready`, announced once. It is not a
separate event stream.** Verified: the only liveness mechanism on the wire is
`gateway.ping`.

Client to server: `{"jsonrpc":"2.0","id":<n>,"method":"gateway.ping","params":{}}`

Server reply, inline in the read loop (`ws.py:420-424`):

```json
{"jsonrpc":"2.0","result":{"ok":true},"id":<n>}
```

Two facts that change client design:

- **It is answered pre-dispatch.** `ws.py:71-74` runs the read loop on a
  per-connection dispatcher task precisely so ping keeps being answered while a
  long handler blocks (issue #108325). Therefore **a successful ping measures
  transport health, not agent responsiveness.** A hung agent turn will not stop
  pings from succeeding.
- The server's own deadline is documented against the client's **45s heartbeat
  deadline**, with `_WS_WRITE_TIMEOUT_S = 10.0` and `_WS_SEND_DEADLINE_S = 30.0`
  (`ws.py:62-69`). A peer that cannot drain ~48 KiB in 30s is dropped.

**Client obligation:** ping on an interval well under 45s. Treat a missed
deadline as a dead transport and reconnect. Do not interpret a successful ping
as "the agent is working".

**A mock must not emit a heartbeat event stream.** It would test a channel the
server does not have.

---

## 6. JSON-RPC envelope and validation

`contracts/base.py`:

- `Params` and `Result` are Pydantic models with **`extra="forbid"`**
  (`base.py:35`, `base.py:42`).
- `Result` serialises with **`exclude_none=False`**, so an explicit `null`
  on the wire is distinguishable from an absent key.
- `Payload` is likewise `extra="forbid"`.

`registry.py:94-109`: only `extra_forbidden` errors are converted to a wire
error. Required/type errors are left to the handler, which owns its own
documented codes (`4006` missing session_id, `4015` bad url, `4124` ...).

**`rpc_dispatch.py:23-24`** - an unknown method is answered with `-32601` and the
message explicitly frames it as **version skew**: the client and the Hermes
backend are out of sync (different versions); run `hermes update` and restart
both.

**Client obligation:** sending an unknown param key is a **client bug** answered
with `4000`, never silently ignored. Do not build "send and hope" params; the
server will reject them loudly, which is the intended behaviour.

---

## 7. Error codes (all verified from source)

| Code | Meaning | Source |
|---|---|---|
| `-32601` | unknown method / **version skew** | `rpc_dispatch.py:23-24` |
| `-32603` | response serialization failure | `transport.py::serialize_frame` |
| `-32000` | handler exception | `rpc_dispatch.py:79` |
| `4000` | params contract violation (unknown key) | `rpc_dispatch.py:29-31`, `registry.py:106-108` |
| `4064` | profile unavailable | `rpc_dispatch.py:36` |
| `5035` | **backend retiring; reconnect to continue** | `rpc_dispatch.py:13`, `rpc_dispatch.py:68` |
---

## 8. `session.create`

`contracts/sessions.py:118-151`.

Params of note: `cwd`, `cwd_explicit`, `title`, `model`, `provider`,
`reasoning_effort`, `fast`, `close_on_disconnect`, `hidden`,
`follow_profile_config`, and **`idempotency_key`**.

```json
{"session_id": "...", "stored_session_id": "...", "message_count": N,
 "messages": [...], "info": {...}}
```

`idempotency_key` exists because a **retried create whose response was lost in
transit must return the SAME session, not a duplicate child** (`sessions.py:136-138`).

**Client obligation:** generate a stable `idempotency_key` per intended create
and reuse it on retry. This is the **only** idempotency guarantee in the surface -
see section 18.

---

## 9. `session.resume`, and stored vs runtime id

`contracts/sessions.py`, `SessionResumeParams` docstring:

> `session_id` is the **STORED id** (or an exact title); the reply's `session_id`
> is the **runtime id**.

Params add: `lazy`, `defer_history`, `omit_messages`, `eager_build`,
`close_on_disconnect`, `inline_images` (default `true`; `false` renders image
parts as `"[image]"` instead of re-transmitting data URIs - explicitly noted as
useful for a remote client, issue #116511).

Result extends `LiveSessionSnapshot`, which carries: `session_id`,
`stored_session_id`, `resumed`, `status` (`idle|starting|waiting|working|
streaming|resuming`), `inflight`, `queued`, `pending_approval`, `open_requests`,
`todo_state`, `auto_continue`, `messages_omitted`, `hydrating`, `running`,
`turn_started_at`.

**Client obligation (confirmed by consultation):**

- **Persist `stored_session_id`.** It is the only stable key.
- **Never cache `session_id` as the resume key.** It is a runtime id and is
  ephemeral across reconnects.
- On reconnect: `session.resume {session_id: <stored_session_id>}`.

Related methods: `session.activate` (attach without closing the previously
focused session), `session.close` (tear down live; the stored row **stays
resumable**), `session.list`, `session.most_recent`, `session.active_list`.

---

## 10. Prompt submission

**The method is `prompt.submit`, declared in `contracts/prompt_voice.py:76`** -
not in `sessions.py`.

Params (`PromptSubmitParams`): `text` (a string, or a structured parts list on
relay/hosted paths), `display_kind` (only `"hidden"` honoured), `interrupted`
(client-side barge-in), `queued` (client queue drain - the busy path **must
hold** it, never redirect/steer), `surface`, `voice_context`, `title_preview`,
and the truncation family (`truncate_before_user_ordinal`,
`truncate_before_row_id`, `truncate_before_message_id`, `confirm_truncate`,
`confirm_empty_truncate`, `rebind_survivor_row_ids`).

Result (`PromptSubmitResult`): `status` is one of `streaming|queued|steered|
redirected`, plus `voice_stopped`, `user_row_id`, `survivor_user_row_ids`,
`survivor_row_id_map`, `turn_isolation`.

**Truncation requires explicit consent.** `confirm_truncate` plus one durable
target; the handler **refuses** truncation without a matching durable target
(`methods_prompt.py:329, 388, 399, 420, 460`). A client that sends an ordinal
alone is refused by design.

**`_turn_author`, `_hosted_task` and `hosted_terminal_callback` are in-process
only** (`Field(exclude=True)`), and a client dict for `_turn_author` is answered
`4124`. Do not attempt to set them.

**`prompt.submit` has NO idempotency key.** See section 18.

---

## 11. Streaming

`message.delta` = one streamed chunk (`{text, rendered?, verbose?}`); the turn
ends at `message.complete`.

**Token coalescing (`ws.py:83`, `_TOKEN_COALESCE_S = 0.033`).** Per-token frames
are **buffered and flushed as a batch** on a short timer; ordering is preserved.
A flush is scheduled with `loop.call_later` (`ws.py:168`).

**Client obligation:** treat `message.delta` as **incremental**, never as
one-frame-per-token. Do not infer token count or timing from frame arrival.

Events that must be **dropped and never surfaced**: `reasoning.delta`,
`reasoning.available`, `thinking.delta`, and `MessageCompletePayload.reasoning`.
These carry chain-of-thought. They are filtered in the adapter so they never
reach the event bus. This is a privacy and policy requirement, not a rendering
choice.

Other observed events: `message.start`, `message.interim`, `status.update`,
`session.usage`, `session.title`, `tool.start`, `tool.complete`,
`tool.generating`, `tool.output_risk`, `todo.updated`, `error`, `notice`,
`session.info`, `session.resume_progress`, `session.reclaimed`,
`approval.cancelled`, `session.control.update`, `background.complete`.

**`5035` is reconnect-mandatory, not fatal.** It is returned when
`retirement.admitted` is false or `retirement.acquire()` fails - the backend is
shutting down but the client should reconnect. A client treating it as a terminal
error will strand sessions.

**`-32601` means version skew, not "bad request".** The remedy named by upstream
is `hermes update` on the remote host. The Android client must surface this
distinctly; retrying will never fix it.

---

## 12. Server-to-client requests â€” the full set

**12 are declared** in `contracts/server_requests.py`. M0-006 mentioned five;
the real surface is larger and the client must not assume an unknown method is
malformed.

| Method | Params | Result |
|---|---|---|
| `clarify` | `questions[1-5]{qid,question,choices?,multi_select}`, `answers?` | `{answers:{qid: str or null}}` |
| `approval` | `request_id`, `command`, `description`, `choices`, `allow_permanent`, `allow_session`, `smart_denied`, `tool_name`, `gateway_session_id`, **`extra="allow"`** | `{choice, all?}` |
| `sudo` | `command` | `{value}` |
| `secret` | `env_var`, `prompt`, `metadata?` | `{value}` |
| `vault.unlock_prompt` | `backend`, `display_name` | `{value}` |
| `vault.save_login` | `origin`, `site` | `{value}` (JSON `{identifier,password}`) |
| `vault.code` | `site?`, `hint?` | `{value}` (2FA) |
| `terminal.read` | `start?`, `count?` | `{value}` (JSON) |
| `preview.read` | `start?`, `count?` | `{value}` (JSON) |
| `window.read` | â€” | `{value}` (JSON) |
| `preview.act` | `action`, `ref?`, `selector?`, `text?`, `key?`, `submit?`, `full?`, `to?`, `amount?`, `max?`, `allow_shortcut?` | `{value}` |
| `tour` | `action`, `surface?`, `selector?`, `title?`, `text?`, `side?`, `steps?`, `step_index?` | `{value}` |

All params inherit `session_id` (`ServerRequestParams`, `server_requests.py:16-19`).

**`ValueResult.value == ""` means skipped / declined** (`server_requests.py:22-26`).
It is never a successful empty answer.

**`clarify` special rule:** a response **without `answers` is cancel-all**
(`server_requests.py:48-50`). Partial answers lock early through the separate
`clarify.lock` RPC.

**Response routing (`rpc_dispatch.py:53-58`):** when the server receives a frame
that answers one of *its* requests, **no response frame goes back**. A response
whose id matches no known server request is **dropped with a debug log** and
must not crash the client â€” this is the resume-race path, and the client must not
resend or desync.

---

## 13. Cancellation

| Mechanism | Contract |
|---|---|
| `session.interrupt` | declared in `contracts/sessions.py` |
| `session.steer` | mid-turn correction (`SessionCorrectionParams`) |
| `session.redirect` | mid-turn correction, same params |
| `prompt.submit {interrupted: true}` | client-side barge-in; the turn's model message carries the note |
| `prompt.submit {queued: true}` | queue drain; **the busy path must hold it**, never redirect/steer |
| `request.cancel` event | withdraws an open server-to-client request (`server_requests.py:5`) |

Result `status` distinguishes `streaming|queued|steered|redirected`, so the client
can tell what the gateway actually did rather than assuming.

---

## 14. Disconnect, reconnect, resume

**Teardown (`ws.py:459-467`).** On WS disconnect the gateway calls
`_close_sessions_for_transport(transport, end_reason="ws_disconnect")`, which
either:

- **reaps** sessions created with `close_on_disconnect`, or
- **detaches** the rest to a drop sentinel so later emits do not hit a closed
  socket, handing them to a **grace-windowed orphan reaper** â€” and
  **"a quick resume cancels it"**.

**This is the reconnect contract.** A prompt in flight at disconnect is not lost:
the session is parked, and a prompt resume can reattach it. This is a
**host-side** behaviour. The client cannot see or rely on the reaper window.

**Replay (`session.events.since`).**

```
{session_id, last_seen?} -> {events, latest_seq, truncated, count,
                             epoch, open_requests}
```

**`truncated: true` means refetch, not patch.** Any state the client derives
incrementally is invalid when this is true.

**Client obligation on reconnect:** persist `stored_session_id`; on reconnect
`session.resume` with it; compare `replay_epoch`; if it changed, drop watermarks
and refetch. If `open_requests` is non-empty, re-deliver each entry to the
request handler â€” an approval asked before the disconnect is **still open**.

---
distinctly; retrying will never fix it.

---
---

## 15. Responsibilities

### Hermes gateway (remote host) — the brain
Owns the agent, model calls, tool execution, memory, session state, workspace
mutation, and all planning/retry semantics.

### Android client (thin)
- WebSocket connection lifecycle, ping/heartbeat, reconnect
- Typed decode per this spec; surface unknown methods rather than guessing
- Track stored vs runtime session id; persist `stored_session_id`
- Render the event stream
- Present server-to-client requests to the user **without deciding them**
- Present errors with their code and meaning

### Android client — explicitly forbidden
- Implement `AIAgent`, tool execution, model calls, or Hermes memory
- Implement Hermes session semantics internally
- Create a second agent loop, retry policy, or planning logic
- **Silently auto-approve any server request**
- Reconstruct a redacted `command` from other fields
- Invent a `task.submit` / `tool.start` protocol
- Treat Android filesystem as source of truth

---

## 16. Security requirements

1. **WSS/TLS.** The trust boundary is now "different host", not "same device"
   (`PIVOT-DECISION.md`). Plaintext `ws://` is not acceptable off-LAN.
2. **Authenticated session.** See §17 — Hermes provides **no** auth mechanism of
   its own, so the mounting host owns it. OD-001 is OPEN.
3. **No credential in the workspace.** Never write a token into the remote
   workspace or any file the agent can read.
4. **No client-side trust escalation.** An inbound frame is data and a request,
   never authority.
5. **`approval` uses an explicit allowlist deserialiser.**
   `ApprovalRequestParams` sets `extra="allow"` (`server_requests.py:81`), so the
   server — or a future version, or a faulty one — may attach arbitrary keys that
   Pydantic will not reject. The client DTO must read **only** `request_id`,
   `command`, `description`, `choices`, `allow_permanent`, `allow_session`,
   `smart_denied`, `tool_name`, `gateway_session_id`, and **discard and log**
   anything else. Passing a raw server dict into the policy engine or the UI
   would let an unexpected key influence behaviour out of band.
6. **`command` is redacted server-side** (`server_requests.py:68`,
   `server_requests.py:101`). Display it as received; never attempt to infer the
   unredacted command.
7. **`ValueResult ""` is declined**, never success.
8. **`sudo` / `secret` / `vault.*` are never auto-answered.**
9. **Chain-of-thought events are dropped** (§11).
10. `ApprovalChoice` is a closed set: `once|session|always|deny`. `always` grants
    persistently and must be an explicit user act.

---

## 17. Authentication — delegated, and still OPEN

**No token, `Authorization` header or bearer check exists anywhere in
`tui_gateway/ws.py`.** Verified by reading the file.

`ws.py` accepts an **`auth_identity` parameter** (`ws.py:98`, and
`WSTransport(..., auth_identity=auth_identity)` at `ws.py:359`), supplied by
**whatever mounts it**. `ws.py:1-4` documents mounting as
`@app.websocket("/api/ws")`, and the comment at `ws.py:103` states
`auth_identity` is the "sole identity authority for browser control".

**Therefore authentication is delegated to the mounting host, and the Android
client cannot solve it alone.** This is **OD-001** in `docs/OPEN-DECISIONS.md` and
remains OPEN. Phase A must not invent a scheme.

M21 Phase A therefore proves the **protocol** with a loopback mock that carries
no authentication, and Phase B records the real host's auth behaviour as evidence.

---

## 18. Unresolved gaps — recorded, not solved

### OD-006 — `prompt.submit` has no idempotency key (NEW, OPEN)

`session.create` has `idempotency_key` explicitly so a retried create returns the
same session (`sessions.py:136-138`). **`prompt.submit` has no equivalent** in
`PromptSubmitParams` (`contracts/prompt_voice.py:26-51`).

The risk this creates is specific and was raised in consultation: **Android may
kill the app mid-call while the remote gateway retains session state.** On
reconnect the client cannot determine whether its prompt was accepted, in-flight,
or already executed. Re-sending risks a duplicate turn; not re-sending risks
---

## 19. Phase A mock — required behaviour set

Consultation found the initially-planned set incomplete. The mock must reproduce
**at least** the following, and the client tests must cover each:

| # | Scenario | Why it is required |
|---|---|---|
| 1 | accept -> `gateway.ready` | lifecycle precondition |
| 2 | `gateway.ping` -> inline `{ok:true}` | transport liveness, pre-dispatch |
| 3 | `session.create` | includes `idempotency_key` |
| 4 | **duplicate `session.create` with the same key** | proves the client does not double-fire |
| 5 | `session.resume` returns a **different** runtime id | proves stored-vs-runtime handling |
| 6 | `prompt.submit` | the real method name |
| 7 | coalesced `message.delta` then `message.complete` | incremental rendering |
| 8 | `approval` server-request, and an answer echoing its id | server-to-client direction |
| 9 | **`approval` carrying an unrecognised extra field** | `extra="allow"` must be handled defensively |
| 10 | **`clarify` answered without `answers` = cancel-all** | distinct semantics, easy to invert |
| 11 | `-32601` unknown method | version skew |
| 12 | **`4000` bad param, distinct from -32601** | different client-bug class |
| 13 | **`5035` backend retiring** | reconnect-mandatory, not fatal |
| 14 | response for an **unknown/stale** server-request id | resume race; must be dropped |
| 15 | `session.events.since` -> **`truncated: true`** | proves refetch, not patch |
| 16 | **`replay_epoch` change** (simulated backend restart) | proves watermark reset |
| 17 | disconnect -> reconnect -> resume by `stored_session_id` | the reconnect contract |

**The mock must NOT emit a heartbeat event stream.** `heartbeat: true` is a
ws-side flag inside `gateway.ready` only; an emitter would test a channel the
server does not have.

**What the mock proves: client correctness only.** It does **not** prove Hermes
integration, and a green mock suite must never be reported as "Hermes works".

---

## 20. Evidence label key

| Label | Meaning |
|---|---|
| **VERIFIED FROM SOURCE** | Read in the pinned source at the cited line |
| **VERIFIED BY BUILD** | Observed in an executed build/test |
| **VERIFIED ON DEVICE** | Observed on the physical phone |
| **INFERRED** | Reasoned from verified facts, not directly observed |
| **UNKNOWN** | Not determined; explicitly open |
| **BLOCKED** | Cannot proceed without a decision or capability |

Every claim in §0-§14 carries **VERIFIED FROM SOURCE** with a `file:line`
citation. Claims in §18 are **UNKNOWN / OPEN** by construction.

**Not yet claimed:** no part of this document is VERIFIED BY BUILD or VERIFIED ON
DEVICE. That is Phase A's work to earn, and a loopback mock can only ever earn
**client** correctness — never Hermes integration.
losing one.

**This is recorded as an open question, not solved here.** Inventing a
client-side dedupe key would be worse than useless: the server has no contract to
recognise it, so it would create a false sense of safety. Do not build
client-side at-most-once semantics the server cannot honour.

`LiveSessionSnapshot.inflight`, `queued` and `user_row_id` are the closest
available signals for inferring state after resume, and `session.status` /
`session.history` may help — **none of these is a guarantee**, and reconciling
them is an owner decision.

### Open-item index

| # | Item | Where |
|---|---|---|
| 1 | Auth/session model over WSS | OD-001 |
| 2 | Workspace concurrency, locking, runtime-failure recovery | OD-005 |
| 3 | Idempotent prompt submission after client death | **OD-006** |
| 4 | Local cache policy | `PIVOT-DECISION.md` — OPEN |
| 5 | APK/artifact delivery (remote host cannot use adb) | OD-002 |

