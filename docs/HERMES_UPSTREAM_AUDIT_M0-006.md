# Hermes Upstream Audit — M0-006

Everything below was read from the upstream repository. Nothing is inferred
from memory. Where a detail was not confirmed from source it is marked
**NOT CONFIRMED** and the M0-006 code does not depend on it.

## Audited revision

| Field | Value |
|---|---|
| Repository | `https://github.com/NousResearch/hermes-agent` |
| Commit | `eaecc99c7ec5b6f37e880a0b69d16871cd3e4f57` |
| Branch | `main` |
| Commit date | 2026-10-03T01:31:33Z |
| Commit subject | `fix(discord): upsert recreated slash commands without a delete-first step (#104399)` |
| Latest tag | `v2026.9.24` (`f97608f178`) |

### A. Version correction — plan is wrong

The project plan references **Hermes v0.20.5**. That tag **does not exist**.
Tags are date-based (`v2026.9.24`, `v2026.9.21`, `v2026.9.14`, `v2026.9.11`,
`v2026.9.7`, `v2026.8.31`, ...). M0-006 therefore targets the audited commit
above. Nothing in the code hard-codes `v0.20.5`.

### Files inspected

```
AGENTS.md
tui_gateway/entry.py
tui_gateway/rpc_dispatch.py
tui_gateway/transport.py
tui_gateway/ws.py
tui_gateway/event_publisher.py
tui_gateway/contracts/base.py
tui_gateway/contracts/registry.py
tui_gateway/contracts/sessions.py
tui_gateway/contracts/events.py
tui_gateway/contracts/server_requests.py
tui_gateway/methods_prompt.py
.python-version
.nvmrc
```

## B. Entrypoint
`tui_gateway/entry.py` (stdio) and `tui_gateway/ws.py` (WebSocket). Both feed
`tui_gateway.server.dispatch`.

## C. Transport / framing

`tui_gateway/transport.py::serialize_frame` is `json.dumps(obj, ensure_ascii=False)`.
`StdioTransport.write` appends `"\n"`.

**Framing is newline-delimited JSON. There is no Content-Length header.**

Unserialisable payloads become a JSON-RPC error frame carrying the original id
(code `-32603`), not a crash.

## D. JSON-RPC shape
`rpc_dispatch.py` and `contracts/base.py`. Envelope `{"jsonrpc":"2.0","id":...,"method":...,"params":...}`.

### Error codes confirmed from source

| Code | Meaning | Source |
|---|---|---|
| `-32601` | unknown method (version skew) | `rpc_dispatch.py` |
| `-32603` | response serialization error | `transport.py` |
| `-32000` | handler exception | `rpc_dispatch.py` |
| `4000` | params contract violation | `rpc_dispatch.py`, `base.py` |
| `4064` | profile unavailable | `rpc_dispatch.py` |
| `5035` | backend retiring | `rpc_dispatch.py` |

**Critical:** `Params` is `extra="forbid"`. An unknown param key is a **client
bug** answered with `4000`, never silently ignored. `Result` is likewise
`extra="forbid"`. `Result` serialises with `exclude_none=False`, so an explicit
`null` is distinguishable from an absent key.

## E. Method catalog (from `contracts/sessions.py`)

35 methods confirmed, e.g. `session.create`, `session.resume`, `session.activate`,
`session.list`, `session.most_recent`, `session.active_list`, `session.delete`,
`session.title`, `session.set_hidden`, `session.archive`, `session.workspace.move`,
`session.cwd.set`, `session.close`, `session.branch`, `session.branch_stored`,
`session.branch_whole`, `session.undo`, `session.save`, `session.status`,
`session.history`, `session.usage`, `session.context_breakdown`, `session.compress`,
`session.interrupt`, `session.steer`, `session.redirect`, `session.events.since`,
`session.events.stats`, `spawn_tree.save/list/load`, `terminal.resize`,
`llm.oneshot`.

Prompt submission is **`prompt.submit`**, confirmed in
`tui_gateway/methods_prompt.py`. Sibling methods in that file:
`clipboard.paste`, `image.attach`, `image.attach_bytes`, `pdf.attach`.

## F. Event catalog (from `contracts/events.py`) — 60 events

Streaming: `message.start`, **`message.delta`**, `message.interim`,
**`message.complete`**, `message.reaction`.

Tool lifecycle: `tool.start`, `tool.complete`, `tool.generating`, `tool.output_risk`.

Readiness: **`gateway.ready`**, `setup.ready`, `session.info`, `sessions.changed`.

Errors: `error` (`ErrorPayload{message: str}`), `notice`.

Replay/reconnect: `session.reclaimed`, `session.resume_progress`.

### Chain-of-thought events — confirmed present upstream

`reasoning.delta`, `reasoning.available`, `thinking.delta`, and
`MessageCompletePayload.reasoning`. `StreamDeltaPayload` carries
`{text, rendered, verbose}` where `verbose` "rides only when the session's
verbose reasoning mode is on".

**M0-006 must drop these at the adapter boundary.** See §Security.

## G. Session lifecycle

`session.create` -> `SessionCreateResult{session_id, stored_session_id,
message_count, messages, info}`.

Two distinct ids are returned and they are not interchangeable:
- `session_id` — the **live runtime** id.
- `stored_session_id` — the **durable** id.

`session.resume` docstring: *"`session_id` is the STORED id (or an exact
title); the reply's `session_id` is the runtime id."* This asymmetry is a real
trap and is documented in `HermesSessionMapping.kt`.

`session.create` params include `cwd`, `cwd_explicit`, `title`, `model`,
`provider`, `reasoning_effort`, `parent_session_id`, `messages`, `cols`,
`source`.

## H. Cancellation
`session.interrupt` -> `SessionInterruptResult{status, interrupted, turn_isolation}`
where `InterruptStatus` is `interrupted` | `not_interrupted`. Optional
`expected_hosted_task_id` guards against interrupting a task that already
replaced this one.

Server->client requests are withdrawn by the **`request.cancel`** event with
`{id, method, reason}` and `RequestCancelReason` in `timeout`, `interrupted`,
`shutdown`, `resolved`, `session_closed`.

## I. Server->client requests — CONFIRMED
`tui_gateway/contracts/server_requests.py`. These are **real request/response**,
not notifications: `dispatch()` routes an inbound response frame to
`server_requests.resolve_response` and returns `None` (no second response).

Confirmed methods: `clarify`, `approval`, `sudo`, `secret`, `vault.unlock_prompt`,
`vault.save_login`, `vault.code`, `terminal.read`, `preview.read`, `window.read`,
`preview.act`, `tour`.

Every server request's params begin with `session_id`. `approval` returns
`{choice: once|session|always|deny, all}`.

An unanswered response for an unknown id is dropped with a debug log — a stale
or duplicate response must not crash the client.

## J. Streaming
`message.delta` = one streamed chunk (`{text, rendered?, verbose?}`). The turn is
terminated by `message.complete`. `ws.py` coalesces tokens on a
`_TOKEN_COALESCE_S = 0.033` timer and flushes as a batch — so the client **must**
treat deltas as incremental and not expect one frame per token.

## K. Health
`gateway.ready` is the **first frame** of a connection:
`GatewayReadyPayload{skin, change_events, replay_epoch, heartbeat?}`.
`gateway.ping` -> `{"ok": true}` (`ws.py`).

## L. Reconnect / replay
`session.events.since{session_id, last_seen?}` ->
`{events, latest_seq, truncated, count, epoch, open_requests}`.
`truncated: true` means the client must refetch state rather than patch it.
`replay_epoch` detects a gateway restart.

## M. Authentication — NOT CONFIRMED
No token, `Authorization` header or bearer check was found in `ws.py` /
`entry.py`. `ws.py` documents being mounted by a host app
(`@app.websocket("/api/ws")`), so authentication is **delegated to the mounting
host**. M0-006 therefore models authentication as an *optional, transport-
supplied* concern and does not invent a scheme. An `AuthenticationFailure` error
exists for when a real runtime is integrated.

## N. Runtime requirements
| Requirement | Value | Source |
|---|---|---|
| Python | 3.14 | `.python-version` |
| Node | 26 | `.nvmrc` |
| ffmpeg / ripgrep / shell | NOT CONFIRMED as gateway requirements | — |

Env vars observed in `entry.py` / `transport.py`:
`HERMES_TUI_SIDECAR_URL`, `HERMES_TUI_GATEWAY_NO_FLUSH`, `HERMES_TEST_ISOLATION`,
`PYTHONUNBUFFERED` (implied by the no-flush note).

The no-flush flag is documented as *only* safe with `-u`/`PYTHONUNBUFFERED=1`,
otherwise the TUI hangs. Relevant to a future Android runtime.

## O. Filesystem expectations — NOT CONFIRMED
`session.cwd.set` and `session.workspace.move` exist, so cwd is client-supplied.
Hermes' own path policy is **not** audited here; M0-006 passes `cwd` as data and
re-validates any path through `WorkspaceBroker` before use.

## P. Security — the invariant that matters

A frame arriving from Hermes is **data and a request**. It is never authority.

Upstream `approval` / `sudo` / `secret` / `vault.*` server requests are the most
dangerous surface: they ask the client for credentials or for a capability
decision. M0-006 therefore **never auto-answers them**. They are surfaced to the
Harness policy layer as pending requests. They are not implemented as
auto-approving handlers.

`reasoning.delta` / `thinking.delta` / `MessageCompletePayload.reasoning` are
dropped in the adapter and never reach `EventBus`.

## Q. Architecture decision

```
HermesRuntime (M0-004, unchanged)
      |
HermesBridge            correlation, timeouts, cancellation, typed errors
      |
HermesProtocolAdapter   JSON-RPC encode/decode, CoT filtering, unknown-value safety
      |
HermesTransport         newline-delimited JSON frames (stdio / ws / fake)
      |
Hermes gateway
```

The UI never sees a JSON-RPC frame.

## R. Not implemented in M0-006
No Termux runtime. No embedded Python. No Chaquopy. No vendored Python. No Node.
No Hermes launched on device. No MCP. No plugins. No production gateway.
No `ToolRouter` policy wiring beyond proving the boundary is not bypassed.

**M0-006 is a protocol-accurate adapter with a fake transport. No claim is made
that Hermes runs anywhere.**