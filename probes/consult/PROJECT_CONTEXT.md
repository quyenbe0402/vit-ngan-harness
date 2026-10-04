# Project Context — vit-ngan-harness

Seeded once into every consultation session. Update this file when project state
changes materially; the next session picks it up automatically.

## What this project is

An Android app (`dev.vitngan.harness`) that is a **thin client** to a remote
`hermes-agent` gateway. Kotlin + Jetpack Compose, Room, Hilt, coroutines,
kotlinx-serialization. No vendored runtime, no embedded Python.

Repo: `C:\dev\vit-ngan-harness` (ASCII path — AGP rejects non-ASCII).
GitHub: `quyenbe0402/vit-ngan-harness`. Baseline branches: `develop`, `main`.

## Architecture — FROZEN, do not relitigate

**Remote Hermes + thin Android client.** The gateway runs on a Linux/CPython
host; the phone connects over `wss://` and renders.

Authoritative documents, in precedence order:

1. `docs/PIVOT-DECISION.md` — the frozen architecture and workspace ownership
2. `docs/OPEN-DECISIONS.md` — OD-001..OD-006, all currently OPEN
3. `docs/HERMES_BRIDGE_SPEC.md` — the protocol implementation contract
4. `docs/STANDING_AUTHORITY.md` — how decisions are delegated
5. `docs/PIVOT_EXECUTION_AUDIT.md` — what actually executes vs. what is parked
6. `docs/RETROSPECTIVE.md` — why M0 went off the critical path

**The phone does not host the agent brain.** No AIAgent in Kotlin, no second
agent loop, no tool execution, no model calls. Hermes remains the agent.

**Version-B is PARKED.** The embedded/Chaquopy work (5 native wheels proven on
a physical device) is preserved, not deleted, and must not be extended. Its
reactivation requires an explicit owner trigger (OD-004).

## Hermes upstream

`NousResearch/hermes-agent` @ `eaecc99c7ec5b6f37e880a0b69d16871cd3e4f57`.
A local detached checkout exists at `C:\dev\hermes-agent`.

The protocol contract is `docs/HERMES_BRIDGE_SPEC.md`, derived from that source
with `file:line` citations. **Read the source; do not recall the protocol.**

Key facts that are easy to get wrong:

- `prompt.submit` is declared in `contracts/prompt_voice.py`, not `sessions.py`
- `gateway.ping` is answered inline pre-dispatch and has **no** contract
- `session.resume` takes the **stored** id and returns a **runtime** id
- 12 server-to-client request methods exist (clarify, approval, sudo, secret,
  vault.unlock_prompt, vault.save_login, vault.code, terminal.read,
  preview.read, window.read, preview.act, tour)
- `heartbeat: true` in `gateway.ready` is a flag, not an event stream
- **No authentication exists anywhere in `tui_gateway/ws.py`** — `auth_identity`
  is a parameter supplied by whatever mounts it. Auth is OD-001.

## Build configuration

minSdk 26, compileSdk/targetSdk 36. Kotlin 2.0.21, AGP 8.7.3, coroutines 1.8.1,
kotlinx-serialization 1.7.3, Hilt 2.51.1, coreLibraryDesugaringEnabled.

M21 Phase A added the app's first network dependency: **OkHttp 4.12.0**,
deliberately pinned to the superseded-but-stable 4.x line. Plus
`mockwebserver` for tests. Platform caveat: Android API 26-28 has no TLS 1.3, so
OkHttp negotiates down to TLS 1.2 there.

Permissions added in Phase A: `INTERNET` and `ACCESS_NETWORK_STATE`, both
unconditionally. The latter is required by
`ConnectivityManager.registerDefaultNetworkCallback` and throws
`SecurityException` at runtime without it.

Network security config: main source set denies cleartext; the debug overlay
permits it for the loopback mock.

## Existing code that must not be redesigned

`core/hermes/` already implements the whole protocol stack:
`HermesTransport` (send/close/isOpen/`incoming: Channel<String>`),
`HermesProtocol.decodeFrame()`, `HermesProtocolAdapter`, `HermesBridge`,
`HermesSessionMapping`, `FakeHermesTransport`.

**12+ unit test classes** already run against `FakeHermesTransport`, including
reconnect tests that CLOSE the transport and take a fresh instance. Phase A must
keep them passing unchanged.

`incoming` is documented as "closed when the transport dies" — so a transport is
one socket lifetime, and reconnect belongs in a supervisor above it.

## Current milestone

**M21 — Remote Hermes Bridge.** Phases are ordered and must not be skipped:

- **Phase C** ✅ COMPLETE — `docs/HERMES_BRIDGE_SPEC.md`
- **Phase A** — Kotlin WebSocket client + deterministic loopback mock
- **Phase B** — real gateway on a Linux host, off-LAN, with a real prompt that
  edits a real file verified on the remote filesystem

**Phase A proves CLIENT CORRECTNESS ONLY.** A loopback mock does not prove Hermes
integration, and a green mock suite must never be reported as "Hermes works".

## Evidence discipline

Every claim carries one label: `VERIFIED FROM SOURCE`, `VERIFIED BY BUILD`,
`VERIFIED ON DEVICE`, `INFERRED`, `UNKNOWN`, `BLOCKED`.

Two failures this project has already committed, which must not recur:

1. **`orjson`** survived several milestones as a supposed Hermes dependency. It
   is not in the closure; the real Rust/PyO3 dependency is `jiter`. The cause
   was inferring dependencies from memory instead of reading metadata.
2. **A second implementation by the same author catches
   implementation-consistency bugs, not spec-comprehension bugs.** A mock and a
   client written by the same reader of the same document will share a
   misreading. Do not describe it as independent verification.

## Working agreements

- Historical documents are immutable: append banners and corrections, never
  rewrite history. Every `docs/M0-*.md` carries a HISTORICAL RECORD banner.
- Nothing is deleted without explicit owner approval, including obsolete
  execution paths.
- Baseline is green and must stay green: `:app:testDebugUnitTest` (348 tests,
  0 failures, 2 skipped) and `:app:assembleDebug`.
- Branch `m21-remote-hermes`; no squash, no force-push, no history rewrite.