# AI Project Context

> Compact, canonical project context. Read this before any significant work.
> Contains **no secrets** and **no credentials** by design.

**Last updated:** 2026-10-02 by Cline (executor)
**Repository:** https://github.com/quyenbe0402/vit-ngan-harness
**Local path:** `C:\dev\vit-ngan-harness` (ASCII path -â‚¬â€ required by AGP)

---

## 1. Project objective

An Android-native AI agent harness (`vit-ngan-harness`) that gives an LLM
agent structured, **policy-gated** control over the device: workspace file
access, process execution, and tool routing, with every security-sensitive
action forced through a trusted policy chain rather than being decided by
the model.

The agent proposes. A trusted engine disposes.

## 2. Canonical architecture

Layered, with a hard trust boundary between agent-controlled input and
trusted execution.

```
ToolCall (from agent / Hermes)
      |
      v
ToolRouter  ......... routing, ordering, result envelope
      |
      v
CapabilityManager ... what is asked for
      |
      v
TrustedPolicyEngine . what is allowed  <-- TRUST BOUNDARY
      |                     ^
      v                     |
WorkspaceBroker ...... trusted policy lives OUTSIDE the workspace
      |
      v
SecurityPathResolver  path canonicalisation + traversal defence
      |
      v
WorkspaceBackend ..... AppPrivateBackend (java.io.File allowed here)
```

Process execution reuses the same chain and ends at
`ProcessManager`, which accepts **only** an authorized `CanonicalPath`.

Supporting subsystems: `EventBus`, `TaskManager`, `RuntimeManager`
(Hermes bridge abstraction), `RepositoryContextEngine`, `CheckpointManager`,
`DiffEngine`.

## 3. Security invariants (non-negotiable)

These outrank all other sources of authority.

| # | Invariant |
|---|----------|
| S1 | Workspace path access flows only through `ToolRouter -â€ â€™ CapabilityManager -â€ â€™ TrustedPolicyEngine -â€ â€™ WorkspaceBroker -â€ â€™ SecurityPathResolver -â€ â€™ backend`. There is no shortcut. |
| S2 | `ProcessManager` **MUST NOT** resolve paths itself. |
| S3 | `ProcessManager` **MUST NOT** accept a raw `String` path. It accepts only an authorized `CanonicalPath`. |
| S4 | Process execution uses **argument lists**. Never `sh -c`, never string concatenation into a shell. |
| S5 | `AppPrivateBackend` may use `java.io.File` internally. Nothing else may. |
| S6 | Trusted policy is stored **outside** the agent-visible workspace. |
| S7 | Workspace content is **untrusted input**. It can never grant capability. |
| S8 | Hermes/model output **never** authorises a security-sensitive operation. It is a request, never a grant. |
| S9 | No `../` traversal, absolute escape, root-prefix collision, or symlink escape may ever yield an authorized path. |

## 4. Milestone state

| Milestone | Status |
|-----------|--------|
| M0-001 Phase 0 (scaffolding) | **COMPLETE** - `0f18167` |
| M0-002 Domain contracts + security foundation | **COMPLETE** - `676068d`, merged to develop as `99cc67d` |
| M0-003 Persistence + eventing | **COMPLETE** - `b7b7b7e`, merged to develop as `21d0a9c` |
| M0-004 Runtime abstraction stubs | **COMPLETE** - `5207fcf` (155 tests pass) |
| M0-005 / M1 | NOT STARTED |

M0-001 established (do not redo): JDK 17.0.20.1 Temurin, AGP 8.7.3,
Gradle 8.10.2, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Hilt 2.51.1,
compileSdk 36 / targetSdk 36 / minSdk 26, namespace + applicationId
`dev.vitngan.harness`, physical target `b36d068a`.

## 5. Current branch

`claude/M0-003-persistence-eventing`

Branches: `main`, `develop`, `claude/M0-001-foundation`,
`cline/M0-001-validation`, `claude/M0-002-domain-contracts`, `claude/M0-003-persistence-eventing`, `claude/M0-004-runtime-stubs`.

## 6. Toolchain

| Component | Value |
|---|---|
| JDK | Temurin 17.0.20.1 |
| Gradle | 8.10.2 (wrapper committed) |
| AGP | 8.7.3 |
| Kotlin | 2.0.21 |
| KSP | 2.0.21-1.0.28 |
| Hilt | 2.51.1 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |
| Path | `C:\dev\vit-ngan-harness` (ASCII -â‚¬â€ AGP rejects non-ASCII) |
## 9. Runtime strategy

Process execution goes through `ToolRouter -â€ â€™ CapabilityManager -â€ â€™
TrustedPolicyEngine -â€ â€™ WorkspaceBroker.resolve() -â€ â€™ ProcessManager`.
Argument lists only (S4). Termux is the intended runtime host; embedded
CPython is declared unsupported.

## 10. Repository workflow

```
develop -â€ â€™ claude/<task> -â€ â€™ review -â€ â€™ cline/<task>-validation -â€ â€™ develop -â€ â€™ main
```
Never work on `main`. Never force-push. Never hard-reset a shared branch.
Inspect the diff before every commit.

## 11. Known risks

| Risk | Impact | Mitigation |
|---|---|---|
| AGP rejects non-ASCII repo paths | Build fails immediately | Path fixed to `C:\dev\...`; do not rename to a diacritic path |
| SDK auto-installs missing Build-Tools | Non-reproducible builds | Pin buildToolsVersion in app config |
| GitHub token was exposed in chat sessions | Credential compromise | Rotate; never store in `.git/config`, files, or docs |
| Global `credential.helper` reads `$GITHUB_TOKEN` | Push depends on an out-of-band env var | Works today; flagged for owner decision |
| JDK 17 is session-scoped in some shells | Gradle may fall back to JBR 25 | Registry `JAVA_HOME` set to Temurin 17 at user level |

## 12. Active decisions

- D1: Security chain ordering is fixed by S1-â‚¬â€œS9 and is not configurable.
- D2: `CanonicalPath` is an opaque type constructible only by
  `SecurityPathResolver` (or trusted internal code), so an unauthorized raw
  string cannot be passed where a `CanonicalPath` is required.
- D3: `EmbeddedPythonRuntimeBackend` is UNSUPPORTED -â‚¬â€ declared, not built.
- D4: Hermes is stubbed, not vendored, at M0-002.

## 13. Unresolved decisions

- U1: Termux backend target SDK / execution protocol (deferred to runtime milestone).
- U2: Checkpoint storage medium (Room schema finalisation).
- U3: `RepositoryContextEngine` ranking heuristic.
- U4: Whether `CapabilityManager` later needs revocation/TTL semantics.

## 14. Advisory-model limitation (honest disclosure)

The execution brief asks for GPT checkpoints A-â‚¬â€œE as an **independent**
architecture and review advisor.

**No such capability exists in this environment.** The available subagent
tool executes the *same* model as the executor, so it is not an independent
reviewer and cannot satisfy the intent of those checkpoints. Rather than
simulate a second opinion, GPT checkpoints are reported as **UNAVAILABLE**,
and review is performed against the security invariants in -3, which are
objective and testable.

The user remains the final authority on architecture and security.

## 15. Definition of Done (M0-002)

- [x] All M0-002 contracts exist and compile (39 main types, compiles clean)
- [x] Unit tests pass - 91 tests, 0 failures, 2 platform skips (24 path-security tests)
- [x] `assembleDebug` succeeds - APK 9930.57 KB
- [x] `docs/AI_PROJECT_CONTEXT.md` updated
- [x] `handoff/reports/M0-002-execution.md` written
- [x] No Hermes implementation, no second agent loop (HermesBridgeTest pins the stub)
- [x] No product UI
- [ ] Device validation - phone NOT_CONNECTED at validation time; not blocking for pure logic

## 7. Android target

Physical device `b36d068a` -â‚¬â€ Xiaomi Redmi 24069RA21C, Android 16,
API 36, `arm64-v8a`, USB-C. Emulator is **never** the primary validation
target.

## 8. Hermes integration strategy

- **Hermes is NOT rewritten.** No second agent loop is created.
- No AIAgent logic is ported into Kotlin.
- Hermes runtime is **not vendored** at this milestone.
- For M0-002, `HermesRuntime` is an **abstraction/stub**. `TermuxRuntimeBackend`
  is a stub. `EmbeddedPythonRuntimeBackend` is an explicitly UNSUPPORTED
  experimental stub. The Hermes bridge is contract/stub only.
- Hermes output is treated as an untrusted request (S8).