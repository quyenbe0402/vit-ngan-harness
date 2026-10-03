# M0-006 Execution Report - Hermes Bridge Foundation

## 1. Upstream revision audited

| Field | Value |
|---|---|
| Repository | `https://github.com/NousResearch/hermes-agent` |
| Commit | `eaecc99c7ec5b6f37e880a0b69d16871cd3e4f57` |
| Branch | `main` |
| Date | 2026-10-03T01:31:33Z |

**The plan's "Hermes v0.20.5" does not exist.** Tags are date-based
(`v2026.9.24` ...). Nothing in the code references `v0.20.5`. Full audit with
exact source paths: `docs/HERMES_UPSTREAM_AUDIT_M0-006.md`.

## 2. Source paths inspected
`tui_gateway/entry.py`, `rpc_dispatch.py`, `transport.py`, `ws.py`,
`event_publisher.py`, `contracts/base.py`, `contracts/registry.py`,
`contracts/sessions.py`, `contracts/events.py`, `contracts/server_requests.py`,
`methods_prompt.py`, `.python-version`, `.nvmrc`.

## 3. Protocol decisions (all evidence-backed)
- **Framing**: newline-delimited JSON (`serialize_frame` + `"\n"`). No Content-Length.
- **Methods used**: `session.create`, `session.close`, `session.interrupt`,
  `session.events.since`, `prompt.submit`, `gateway.ping`.
- **Events used**: `gateway.ready`, `message.start/delta/interim/complete`,
  `tool.start/complete/output_risk`, `error`, `notice`, `session.info`,
  `session.title`, `sessions.changed`, `request.cancel`.
- **Error codes**: `-32601`, `-32603`, `-32000`, `4000`, `4064`, `5035`.
- **`extra="forbid"`** upstream: unspecified params are omitted, never sent as null.
- **Server->client requests are real request/response** (`clarify`, `approval`,
  `sudo`, `secret`, `vault.*`), not notifications.

## 4. Session mapping
Five distinct ids documented in `HermesSessionMapping.kt`: harness taskId,
harness sessionId, Hermes **runtime** session id, Hermes **stored** (durable) id,
transport id, JSON-RPC id. The upstream trap - `session.resume` takes the
*stored* id and returns a *runtime* id - is modelled explicitly and tested.

## 5. Architecture
```
HermesRuntime -> HermesBridge -> HermesProtocolAdapter -> HermesTransport -> gateway
```
The UI never sees a JSON-RPC frame.

## 6. Security
- `reasoning.delta`, `reasoning.available`, `thinking.delta` and
  `MessageCompletePayload.reasoning` are **dropped** in the adapter and never
  reach the EventBus or UI. Tested.
- Credential server requests (`sudo`, `secret`, `vault.*`) are **never**
  auto-answered; answering returns `AuthenticationFailure` and the request stays
  routable. Tested.
- `approval` is never auto-approved on arrival. Tested.
- The bridge holds **no** CapabilityManager / TrustedPolicyEngine /
  WorkspaceBroker / ProcessManager - asserted by reflection over its
  constructor. Any tool execution must still route
  Hermes -> ToolRouter -> CapabilityManager -> TrustedPolicyEngine ->
  WorkspaceBroker / ProcessManager.
- Static scan: no process execution, no filesystem access, no network calls, no
  Android permissions added.

## 7. Tests
**120 new M0-006 tests**, all green:

| Suite | Tests |
|---|---|
| HermesProtocolTest | 17 |
| HermesBridgeSessionTest | 13 |
| HermesBridgeStreamingTest | 16 |
| HermesBridgeCancellationTest | 11 |
| HermesBridgeErrorTest | 14 |
| HermesBridgeReconnectTest | 13 |
| HermesServerRequestTest | 12 |
| HermesSerializationTest | 14 |
| HermesBridgeSecurityTest | 10 |

Coverage includes create session, submit prompt, streamed events, final
response, correlation ids, cancellation, timeout, malformed JSON, unknown
events, disconnect, reconnect, epoch change, duplicate response, late response,
server-request correlation, expiry, unicode, and no-authorisation-bypass.

**Three real defects were found and fixed by these tests**, not by inspection:
1. A duplicate response overwrote the result a caller had already received.
2. A late response could turn a settled timeout into a success.
3. `"id": null` decoded to the literal string `"null"`.

## 8. Build
```
:app:testDebugUnitTest        BUILD SUCCESSFUL - 299 tests, 0 failures, 2 skipped
:app:connectedDebugAndroidTest BUILD SUCCESSFUL - 12 tests on 24069RA21C - 16
:app:assembleDebug            BUILD SUCCESSFUL
```

## 9. GPT review
**GPT_ADVISOR_UNAVAILABLE.** `OPENAI_API_KEY` is not set, no openai provider is
configured, and the available subagent mechanism runs the same model as the
executor. Not simulated.

## 10. NOT implemented (by design)
No Termux runtime. No embedded Python. No Chaquopy. No vendored Python. No Node.
No Hermes launched on device. No MCP. No plugins. No production gateway.
No Hermes core rewritten. No second agent loop. No duplicated Hermes session,
tool, provider or memory logic.

**No claim is made that Hermes runs anywhere.** M0-006 is a protocol-accurate
adapter with a fake transport.

## 11. Remaining runtime work (future milestone)
- Concrete `HermesTransport` over stdio and/or WebSocket.
- Runtime startup: Python 3.14, Node 26, `PYTHONUNBUFFERED=1`.
- Authentication (upstream delegates it to the mounting host - unresolved).
- Feed bridge events into the existing `EventBus`.
- Wire server-request policy decisions to `CapabilityManager`.