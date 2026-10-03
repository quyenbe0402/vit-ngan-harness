# M0-007 Final Review — GO / NO-GO for Hermes Runtime

**Reviewed revision:** `NousResearch/hermes-agent` @ `eaecc99c7ec5b6f37e880a0b69d16871cd3e4f57`
**Moving upstream `main` was NOT substituted** for the pinned revision.
**Branch:** `cline/M0-007-runtime-feasibility`
**NO-GO** for production Hermes runtime implementation. Evidence for a
feasibility spike is strong; evidence for a production runtime is not.

## 1. Audit verification

I re-read `docs/M0-007_RUNTIME_FEASIBILITY_AUDIT.md` and re-derived its central
claims from the audited revision rather than accepting them.

| Audit claim | Re-verified? | Result |
|---|---|---|
| `requires-python = ">=3.11,<3.15"` | yes, `pyproject.toml` | confirmed |
| `.python-version` = 3.14, `.nvmrc` = 26 | yes | confirmed |
| Core deps gated on `python_version >= '3.14'` | yes, recounted | **45 of 45, not 46** |
| Termux ships Python 3.14.6 aarch64 | yes, termux-main pool | confirmed |
| Termux ships Node 26.4.0 aarch64 | yes, termux-main pool | confirmed |
| Upstream declares `[termux]` / `[termux-all]` | yes | confirmed |
| Android wheel count 0 for the pinned native set | yes, PyPI per pin | confirmed |

### Defect found in the audit: 46 -> 45

The audit, and the Kotlin constant derived from it, said **46** core
dependencies are gated on Python 3.14. Recounting the `[project] dependencies`
block at `eaecc99c` gives **45 entries, all 45 gated** — there is not one
ungated core dependency. The audit also did not note the SQLite/state story at
all (see §6).

The verdict does not change, and in fact the true figure is *stronger*: an
interpreter below 3.14 installs **none** of Hermes' core dependencies, so it
cannot work at all. Corrected in the audit doc and in
`RuntimeFeasibilityAudit.GATED_DEPS_ON_PYTHON_314 = 45`.

I did not correct anything else from general knowledge; the audited revision
stands.

## 2. Evidence status

**Verified from source at `eaecc99c`**
- interpreter floor `>=3.11,<3.15`; 45/45 core deps gated on 3.14
- Termux extras exist; uvloop deliberately omitted (libuv configure fails)
- `psutil` is excluded on Android and replaced by a git-pinned build
- `nemo-relay` marker explicitly excludes Android
- build backend `setuptools.build_meta` with a `wheel` build-require
- gateway framing / methods / events (M0-006 audit)

**Verified experimentally (external, read-only)**
- Termux pool publishes `python_3.14.6-1_aarch64.deb`, `nodejs_26.4.0-1_aarch64.deb`
- PyPI Android wheel availability per pinned version

**Verified experimentally (device, non-destructive)**
- API 36, arm64-v8a, SELinux Enforcing, 170 GB free
- `com.termux` **not installed**; no `python3`/`node`/`npm` on PATH

**Inferred (not experimentally proven)**
- that a full `hermes-agent[termux]` install will *complete* on this device
- install duration on ARM64
- that Termux stays resident under the Android lifecycle
- that sdist builds (cryptography, numpy, Pillow, sentencepiece, soundfile,
  brotlicffi) succeed on Bionic — plausible from the ecosystem, unproven here

**Unknown**
- Hermes authentication model (delegated to the mounting host upstream)
- exact `[termux]` transitive closure size
- Termux wake-lock / foreground-service behaviour on this OEM's ROM
- `hermes-state` SQLite durability characteristics on Android (see §6)

**Blocked**
- no Hermes process has ever been started on the device
- no Termux instance exists to start one
- so no runtime claim can be validated end-to-end

## 3. Termux result — **FEASIBLE**

| Dimension | Assessment |
|---|---|
| Interpreter | **3.14.6 aarch64 available**; satisfies floor and all 45 gated deps |
| Node | **26.4.0 aarch64** available; matches `.nvmrc` = 26 |
| Native deps | 0 Android wheels for most; Termux userland builds them from sdist (inferred, not proven) |
| Subprocess | Full POSIX userland — satisfies Hermes' process-heavy design |
| Filesystem | Real filesystem with shell; Hermes needs `cwd`, `state.db`, skills, plugins |
| Networking | Termux has network; upstream uses `truststore` against the OS CA store |
| Session/state | stdlib `sqlite3` in WAL mode — works on a real filesystem, see §6 |
| MCP/plugins | Declared in `[termux]`; rely on subprocess model that Termux provides |
| Lifecycle | Separate process; Android may kill it — needs a wake lock (**unknown on this OEM**) |
| Security | **Advantage**: separate UID/sandbox, so a Hermes compromise does not grant Harness memory |
| Blockers | Not installed; no install attempted; user install required |

## 4. Embedded Python / Chaquopy result — **NOT FEASIBLE**

| Dimension | Assessment |
|---|---|
| Interpreter | **No Android CPython reaches 3.14.** Chaquopy targets ≤3.13 |
| Dependency closure | 45/45 core deps gated on 3.14 → on ≤3.13, **zero** core deps install. Hermes installs and then fails at first import |
| Native deps | Android wheel count **0** for cryptography, numpy, Pillow, sentencepiece, soundfile, brotlicffi, faster-whisper at the pins |
| Subprocess | Blocked: app-sandbox `execve` of other binaries is denied by SELinux (Enforcing here) and W^X on `app_data` since Android 10 |
| Node | No in-process Node for Android; `.nvmrc` = 26 unattainable |
| Filesystem | Confined to `filesDir`; no `cwd`, no shell, no external tools |
| Networking | Possible but not the blocker |
| Session/state | SQLite in an app sandbox is viable, but irrelevant given the above |
| Security | Worse: Hermes code would run **inside** the Harness process |
| Blockers | Interpreter floor, wheel availability, subprocess model, Node |

Rejected on evidence, not preference. The abstraction is **kept** so the
decision can be revisited if upstream relaxes its Python floor.

## 5. Alternative runtime result

**Remote/Linux host over WebSocket — VIABLE, not selected.**
`tui_gateway/ws.py` exposes a WebSocket server and the M0-006 transport already
carries WebSocket frames. It hosts Hermes off-device, which is outside the
stated goal, and adds a network trust boundary. Recorded, not chosen.

**Vendored/native build of Hermes — REJECTED.** Would require patching Hermes
itself; violates the no-rewrite invariant.

## 6. Gap found: Hermes session/state on Android

The original audit was silent on this. `hermes_state_dbfile.py` at `eaecc99c`
uses **stdlib `sqlite3`** with **WAL mode** and `state.db-wal` / `-shm`
sidecars, and upstream's own comments show WAL is load-bearing (they work
around WAL failures rather than disabling it). `aiosqlite` is only in the
`matrix` extra, gated `sys_platform == 'linux'`, so the state path is the
synchronous stdlib one.

Implication: Hermes needs durable WAL-mode SQLite in its working directory. On
Termux that is a normal filesystem and should work. **Unknown until measured:**
Android's filesystem behaviour for `-wal`/`-shm` under memory pressure, and
Termux backup/restore interaction. This must be measured, not assumed, before
any claim about session durability.

## 7. Security review

| Control | Status |
|---|---|
| `WorkspaceBroker` | Intact. Untouched by M0-007; `git diff develop..branch` touches no workspace file |
| `CapabilityManager` | Intact. Untouched |
| `TrustedPolicyEngine` | Intact. Untouched |
| `ProcessManager` | Intact. Untouched |
| Bridge holds policy objects | **PASS** — all mentions in `core/hermes` are doc comments, verified by scan |
| Raw untrusted paths | None added; no path handling in M0-007 |
| Shell-based bypass | None added; no process execution anywhere in `app/src/main` |
| Credential auto-approval | **PASS** — `sudo`/`secret`/`vault.*` refused by `answerServerRequest`, covered by tests |
| Reasoning/thinking leakage | **PASS** — dropped at the adapter; `hermesActuallyRuns()` is `false` |
| LLM output as authority | **PASS** — M0-007 is audit data only, no execution path |

M0-007 adds no code that can reach a capability, a path, or a credential.

## 8. Lifecycle / process review

- Hermes gateway is a long-lived child with its own event loop and worker pool.
- Termux is a **separate process, separate UID**. Its sandbox is independent of
  the Harness's, so path authorisation must be re-established on the Hermes
  side; it must never be assumed to inherit `WorkspaceBroker`'s decisions.
- A Termux death must surface as `TransportUnavailable`, never as a Harness crash.
- Reconnect, `replay_epoch` and `session.events.since` already exist in the bridge.
- Foreground-service and wake-lock behaviour on this OEM is **unmeasured**.

## 9. Exact recommended next implementation target

**Eligible backend: `TermuxRuntimeBackend`** (inside the existing tree, which
stays as specified):

```
HermesRuntime
├─ TermuxRuntimeBackend          <- eligible next
├─ EmbeddedPythonRuntimeBackend  <- retained, rejected for this revision
└─ FutureRuntimeBackend
```

No second agent loop. Hermes remains the brain.

**Prerequisites before Hermes can genuinely run:**

1. **User installs Termux** — cannot be automated, must not be.
2. **Termux provisions `python` (3.14.x), `nodejs`, `pkg`, `clang`, `make`, `git`.**
3. **An `hermes-agent[termux]` install completes on device** — the real
   unknown. Several sdist builds may take a long time on ARM64.
4. **`tui_gateway.entry` starts and emits `gateway.ready`.**
5. **`TermuxTransport`**: pipe + newline-delimited JSON, per the audited framing.
6. **`TermuxRuntimeBackend`**: start, health, readiness, reconnect, shutdown,
   crash detection, log capture.
7. **Wake lock / foreground service** so Termux stays resident.
8. **Measure SQLite WAL durability** on device before trusting session state.

**Blockers to solve first:** Termux absent (1); `[termux]` closure unproven (3).

**Must NOT be implemented yet:**
- Chaquopy / embedded Python / vendored Python
- Hermes packaged into the APK
- MCP server integration, plugin or skill installation
- root, Device Owner, Accessibility, MediaProjection, VPN
- M1
- any code or UI implying Hermes already runs

## 10. GO / NO-GO

### **NO-GO** for production Hermes runtime implementation.

Rationale, strictly on evidence:

- Termux is the right architecture and the interpreter exists — but **no Hermes
  process has ever been run on the device**, and Termux is not installed.
- The one claim that decides production viability — that
  `hermes-agent[termux]` installs and starts on this ARM64 device — is
  **inferred, not proven**.
- Two further unknowns are material and unmeasured: process residency under
  this OEM's lifecycle policy, and SQLite WAL durability.

**GO for a bounded feasibility spike** whose sole purpose is to install Termux,
provision the interpreter, attempt the `[termux]` install, start
`tui_gateway.entry`, and capture `gateway.ready`. That spike produces the
evidence this review found missing. It is not a production milestone and must
not add app-facing runtime code.

**NO-GO does not mean the architecture is wrong.** It means the evidence is not
yet sufficient to build on.

## Validation for this review

```
:app:testDebugUnitTest          BUILD SUCCESSFUL - 316 tests, 0 failures, 2 skipped
:app:connectedDebugAndroidTest  BUILD SUCCESSFUL - 12 tests on 24069RA21C - 16
:app:assembleDebug              BUILD SUCCESSFUL
```

No production runtime code was added during this review. Changes are limited to
review documentation and the 46->45 correction.