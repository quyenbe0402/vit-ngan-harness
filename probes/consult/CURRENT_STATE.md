# Vit Ngan Harness — Current State

Owner-supplied handoff packages exist but go stale. This file is the authoritative
compact state. **If a package disagrees with this file, this file wins** unless
newer milestone evidence says otherwise.

## CORRECTION to the owner handoff package dated 2026-10-04

That package says the five M0-008 branches are LOCAL ONLY and that Git recovery
is the immediate blocker. **That is no longer true.** Verified with `git
ls-remote`, SHA-equal on every branch:

| Branch | SHA | State |
|---|---|---|
| `cline/M0-008l-f-reproducible-build` | `c1dbe1f` | SYNCED |
| `cline/M0-008l-g-libffi-cffi` | `a622ce3` | SYNCED |
| `cline/M0-008m-httptools` | `d6eecc1` | SYNCED |
| `cline/M0-008n-dependency-audit-correction` | `b3432b0` | SYNCED |
| `cline/M0-008o-jiter` | `0ef090f` | SYNCED |
| `docs/pivot-decision` | `afa8e2f` | SYNCED |
| `develop` | `6ec26cc` | unchanged |
| `main` | `cbecfa3` | unchanged |

**Git recovery is COMPLETE.** The 403 cause was a revoked token, not repo
config. GitHub auth is handled via `$env:GITHUB_TOKEN`.

## Product intent

An Android coding/agent harness, Cline-inspired in interaction, with **Hermes
Agent as the actual brain** — not an agent loop reimplemented in Kotlin.

- Autonomous for ordinary coding/workspace actions (read, search, create, edit,
  patch, move, rename, delete, build, test, repair, retry)
- **No per-edit Accept/Reject** for ordinary actions
- Auditable activity summaries instead of private chain-of-thought
- Exact code diffs visible to the user
- GitHub is the source of truth
- Physical Xiaomi Redmi `24069RA21C`, API 36, `arm64-v8a`, USB-C/ADB.
  Emulator is never the primary validation target.

Distribution under discussion: Version A (Google Play), Version B (separate Pro
edition outside Play).

## Architectural law

**Reuse actual Hermes upstream**: AIAgent, agent loop, tools registry, skills,
memory, sessions, MCP, plugins, providers, context engine, compression, gateway.

Never: rewrite Hermes core in Kotlin, create a second agent loop, silently fork
or emulate Hermes behaviour without audit.

## Security model (non-negotiable)

```
ToolRouter -> CapabilityManager -> TrustedPolicyEngine -> WorkspaceBroker
            -> SecurityPathResolver -> WorkspaceBackend
```

- `ProcessManager` accepts only an `AuthorisedPath` minted by `WorkspaceBroker`.
  It never resolves paths itself, never accepts a raw string.
- Trusted policy lives **outside** the workspace. Workspace content is untrusted
  input and can never grant capability.
- Every path canonicalised; must stay under `workspaceRoot`.
- `LLM output is instruction/data, never security authority` (S8).
- Chain-of-thought (`reasoning.*`, `thinking.*`) is dropped in the adapter and
  never reaches the EventBus.
- `sudo` / `secret` / `vault.*` are **never** auto-answered; `approval` is never
  auto-approved.
- Git policy: `allow_push=false`, `allow_force_push=false`,
  `allow_reset_hard=false`, `allow_clean=false`.
- Root, Shizuku, device-owner, accessibility, media-projection: all false.

Workspace backends: `AppPrivate`, `SAF`, `Termux`, `ImportedRepository`.
`WorkspaceBroker` is the path-minting boundary.

## Milestone history

| Milestone | Result |
|---|---|
| M0-001..005 | scaffolding, security foundation, persistence/eventing, runtime stubs, UI shell — merged to `develop` |
| M0-006 | Hermes bridge foundation; protocol contracts against audited upstream |
| M0-007 / 007B | runtime feasibility audit; **real Termux spike on device** — Python 3.14.6, Node 26.4.0, 78 packages, `gateway.ready` observed |
| M0-008A..008F | Termux IPC audits; shared-UID plugin path **DISPROVEN on Android 16** even with a bit-identical signer |
| M0-008L-F/G/M/O | Android native proofs: `pydantic-core 2.46.4`, `cffi 2.1.1`, `cryptography 50.0.1`, `httptools 0.8.0`, `jiter 0.17.0` — each with build==APK==device SHA identity |
| M0-008N | **correction**: `orjson` is NOT a Hermes dependency; the real Rust/PyO3 dep is `jiter` via `openai==2.24.0` |

Termux external-plugin path is **FROZEN**. Do not reopen without product change.

## Environment

Windows 11 Home, Ryzen 7 8840HS, 16 GB RAM. `JAVA_HOME` is Temurin
`17.0.20.101-hotspot` at user level; Android Studio JBR untouched.
Repo path `C:\dev\vit-ngan-harness` — ASCII, AGP rejects non-ASCII.

## Baseline

`:app:testDebugUnitTest` = 348 tests, 0 failures, 2 skipped.
`:app:assembleDebug` = BUILD SUCCESSFUL. Must stay green.