> **HISTORICAL RECORD.**
> This document records the state and evidence at the time of its milestone.
> Its historical contents are preserved unchanged, including any conclusion
> later overtaken by newer evidence or by the architecture pivot. Do not read
> it as current architecture guidance.
> Current architecture decisions are governed by `docs/PIVOT-DECISION.md`.

# M0-007 Runtime Feasibility Audit

**Milestone type:** AUDIT / FEASIBILITY. Nothing was implemented here.
**Verdict in one line:** Termux-hosted is **FEASIBLE**; in-process embedded Python is
**NOT FEASIBLE**. Termux is selected, on evidence.

## 1. Scope

Determine which Android runtime strategy can actually host the audited Hermes
revision on the user's physical ARM64 device. Evaluate Termux-hosted Hermes,
in-process embedded Python / Chaquopy, and any alternative the evidence reveals.
This document audits; it does not build a runtime.

## 2. Audited Hermes revision

| Field | Value |
|---|---|
| Repository | `NousResearch/hermes-agent` |
| Commit | `eaecc99c7ec5b6f37e880a0b69d16871cd3e4f57` |
| Date | 2026-10-03 |
| Version scheme | date-based tags (`v2026.9.24`, ...). No `v0.20.5`. |

## 3. Actual Hermes runtime requirements (from audited source)

Read from `pyproject.toml`, `.python-version`, `.nvmrc` at `eaecc99c`:

- `requires-python = ">=3.11,<3.15"`
- `.python-version` = **3.14**
- `.nvmrc` = **26**

Dependency shape is the decisive fact:

- **45 core dependencies are gated on `python_version >= '3.14'` — that is *all* of them.** They are
  simply not installed on an older interpreter. A 3.11/3.12/3.13 interpreter
  yields a Hermes install with no LLM client, no HTTP stack and no UI toolkit.
- Every direct dep is exact-pinned (`==X.Y.Z`), by deliberate policy against
  supply-chain attacks. Upgrading an interpreter therefore does not silently
  change the dependency set.
- `psutil==7.2.2` is marked `sys_platform != 'android'` — upstream already
  excludes Android.
- Platform markers in use: `win32` x30, `darwin` x10, `linux` x8, `android` x7.

### Upstream ships Android/Termux support explicitly

This is the single strongest finding. `pyproject.toml` at the audited commit
defines dedicated extras:

- `[termux]` — "Baseline Android / Termux path for reliable fresh installs"
- `[termux-all]` — best-effort extended profile

with comments stating that **uvloop cannot build on Android/Termux** (libuv's
`./configure` fails) and that the `[termux]`/`[termux-all]` profiles
intentionally omit it. Upstream also notes Termux Python "reports plain
linux/aarch64 but runs on Bionic".

Upstream PR #100574 (merged) is titled *"fix(install): select a supported Python
on Termux, with TUR fallback"*, fixing a bug where the installer provisioned
Python 3.14.6 on Termux which the then-current `requires-python` rejected.
**Hermes at the audited commit is a supported Termux target.**

## 4. Termux feasibility — FEASIBLE

### Evidence: the interpreter exists

Termux package pool (`termux-main`, aarch64):

```
python_3.14.6-1_aarch64.deb      4.6 MiB   July 5, 2026
nodejs_26.4.0-1_aarch64.deb
nodejs-lts_24.18.0-1_aarch64.deb
```

- **Python 3.14.6 aarch64** satisfies `requires-python >=3.11,<3.15` **and**
  satisfies the `python_version >= '3.14'` marker on all 45 gated deps.
  The version conflict that broke Termux installs earlier has since been
  resolved in Hermes' favour.
- **Node 26.4.0 aarch64** exactly matches `.nvmrc` = 26.

### Evidence: the native deps are obtainable on Termux

`android` is a valid PyPI platform tag, so Android wheels *can* exist. Querying
PyPI for the exact upstream pins:

| Package | Pin | android wheels | manylinux aarch64 | note |
|---|---|---|---|---|
| resvg-py | 0.4.0 | **2** | 4 | Android wheels present |
| cryptography | 50.0.1 | 0 | 11 | builds from sdist on Termux |
| numpy | 2.4.3 | 0 | 7 | builds from sdist |
| Pillow | 12.3.0 | 0 | 9 | builds from sdist |
| sentencepiece | 0.2.2 | 0 | 8 | builds from sdist |
| psutil | 7.2.2 | 0 | 3 | excluded on android anyway |
| soundfile | 0.14.0 | 0 | 1 | builds from sdist |
| brotlicffi | 1.2.0.2 | 0 | 3 | builds from sdist |
| faster-whisper | 1.2.1 | 0 | **0** | no aarch64 wheel at all |

Pure-Python deps (pydantic, httpx, requests, rich, prompt_toolkit, websockets,
fire, tenacity, croniter, Markdown, pathspec, packaging) need no compilation.

Termux's build tooling (`clang`, `make`, `pkg`) handles the sdist builds. The
audited `pyproject.toml` itself acknowledges deps "must build from sdist" on
Android.

### Why Termux works where in-process Python does not

Termux runs a real POSIX userland on Bionic with a package manager, a compiler
and a shell. That is the environment the sdist builds assume. An in-process
embedded interpreter has none of that.

### Termux limitations (recorded, not waved away)

- Termux is **not currently installed** on the device (verified below).
- Requires user install from F-Droid/GitHub and a user grant — cannot be done
  silently from the Harness.
- Hermes runs as a **separate process**; the Harness talks to it over stdio or
  WebSocket, exactly as `HermesBridge` already models.
- Android may kill the Termux process; needs a wake lock to stay resident.
- Node 26 is present but Hermes' Node usage is for the TUI, not the gateway —
  the gateway path is Python and is the part the Harness needs.
- `[termux]` intentionally omits uvloop. Irrelevant to the gateway path.

## 5. Embedded Python / Chaquopy feasibility — NOT FEASIBLE

This is not a close call, and the reason is the interpreter, not Python itself.

### Blocking: Python 3.14 does not exist for Android

Hermes requires Python >= 3.11 and gates **45 core dependencies** on
`python_version >= '3.14'`. Chaquopy targets CPython 3.8–3.13 and has no 3.14
build. Installing a 3.13-ABI interpreter yields an install where
`openai`, `httpx`, `requests`, `pydantic`, `rich`, `prompt_toolkit`,
`websockets`, `croniter` and others are **all absent** — because their markers
evaluate false. Hermes would install and then fail at first use with an
import error, which is the worst failure shape: it looks installed.

### Blocking: no Android wheels

For the exact pins upstream requires, Android wheel count is **0** for
cryptography, numpy, Pillow, sentencepiece, soundfile, brotlicffi, psutil and
faster-whisper. Only `resvg-py` ships Android wheels. Chaquopy cannot build
from sdist for this set — it consumes prebuilt ABI wheels, and cross-compiling
the sdist set into an app is a separate project with no upstream support.

### Blocking: subprocess assumptions

Hermes is built around spawning processes — tool execution, the TUI gateway
entrypoint, MCP servers, PTY. Android's `execve` on other apps' binaries is
blocked by SELinux (Enforcing on the test device) and the W^X restriction on
`app_data` since Android 10. An in-process interpreter inherits the *app's*
sandbox, so it can spawn nothing useful and reach no filesystem outside
`filesDir`.

### Blocking: Node 26

`.nvmrc` = 26. There is no in-process Node runtime for Android, and Hermes'
TUI/JS surface would have to be excluded — meaning Hermes would be running in a
reduced configuration that upstream does not support.

### Conclusion

**NOT FEASIBLE.** Chaquopy-class embedding cannot satisfy the audited Hermes
dependency closure on Android ARM64. This is a property of the audited
revision's requirements, not of Python.

## 6. Alternative runtime findings

### A. Self-contained native binary — rejected
Hermes is a large pure-Python application with a Python-version-gated
dependency graph. A native build would require vendoring and patching Hermes
itself, which violates the no-rewrite invariant.

### B. Remote/Linux host over WebSocket — viable but out of scope
`tui_gateway/ws.py` exposes a WebSocket server. The Harness's existing
`HermesTransport` abstraction can carry it with no protocol change. But this
hosts Hermes off-device, which is not the stated goal, and it introduces a
network trust boundary. Recorded for a future milestone, not selected.

### C. Termux-hosted Hermes, stdio or WebSocket — **SELECTED**
Termux is the only candidate that satisfies the interpreter, the native
dependency closure, the subprocess model and the upstream support statement.

## 7. Physical-device validation

Collected on `b36d068a` via adb (non-destructive; nothing installed):

| Property | Value |
|---|---|
| Model | 24069RA21C (Xiaomi Redmi) |
| Android | 16 |
| API level | 36 |
| ABI | arm64-v8a |
| SELinux | **Enforcing** |
| Free storage | 170 GB available on `/data/user/0` (227 GB total, 26% used) |
| Termux installed | **No** (`com.termux` absent) |
| python3/node/npm on PATH | **None** |
| F-Droid | not installed; `com.android.chrome` and `com.android.browser` present |
| ADB | connected, authorized |

Interpretation: storage and ABI are not constraints. The interpreter is absent
and must be installed by the user. SELinux Enforcing confirms no attempt to
relax security policy is required or attempted.

## 8. Dependency closure

**Must exist for a Termux-hosted gateway:**
`python (3.14.x)`, `nodejs`, `pkg`, `clang`, `make`, `git`, plus the
`hermes-agent[termux]` extra: `python-telegram-bot[webhooks]==22.8`, `[cron]`,
`[mcp]`, `[acp]`.

**Satisfied by Termux:** Python 3.14.6 aarch64, Node 26.4.0 aarch64.
**Built from sdist on device:** cryptography, numpy, Pillow, sentencepiece,
soundfile, brotlicffi.
**Pure Python, no build:** openai, httpx, requests, pydantic, rich,
prompt_toolkit, websockets, fire, tenacity, croniter, Markdown, pathspec.
**Android wheel available:** resvg-py.
**Excluded on Android by upstream:** psutil, uvloop.
**Unavailable (voice extra, not needed):** faster-whisper has no aarch64 wheel.

## 9. Blocking issues

1. **Termux is not installed.** The user must install it. Cannot be automated
   silently, and must not be.
2. **No Termux instance has been executed.** No Hermes process has been started
   on the device. Nothing in this document claims otherwise.
3. **faster-whisper is unavailable** on aarch64 — only blocks the `voice` extra,
   not the gateway.
4. **Process lifetime**: Android may kill Termux; a wake lock is required.
5. **Install time**: sdist builds on an ARM64 phone are slow and may take tens
   of minutes.

## 10. Security implications

- Hermes runs as a **separate process in a separate sandbox**. That is a
  security *benefit*: the Harness never loads Hermes code into its own process,
  and a Hermes compromise does not grant Harness memory.
- The Harness-to-Hermes link is a pipe or socket. It must still be treated as
  **untrusted input** — unchanged from M0-006. The bridge grants no capability.
- Tool execution must continue to route
  Hermes -> ToolRouter -> CapabilityManager -> TrustedPolicyEngine ->
  WorkspaceBroker / ProcessManager. A Hermes running in Termux has its own
  filesystem view; it must not be permitted to bypass WorkspaceBroker.
- Termux's sandbox is independent of the Harness's. Two sandboxes mean path
  authorisation must be re-established on the Hermes side rather than assumed.
- SELinux stays Enforcing. No root, Device Owner, Accessibility or
  MediaProjection is requested by this milestone.

## 11. Lifecycle / process implications

- Hermes gateway is a long-lived child process with its own event loop and
  worker pool (`rpc_dispatch.py` pools long handlers).
- It must survive the Harness process being killed and reconnected to: the
  bridge already supports reconnect, `replay_epoch` and `session.events.since`.
- Android foreground-service and wake-lock policy will govern whether Termux
  stays resident. This must be observed, not assumed, in a future milestone.
- The Harness must treat a Termux death as `TransportUnavailable`, not as a
  Hermes crash in its own process.

## 12. Recommended architecture (evidence-based)

**Selected: Termux-hosted Hermes, connected via `HermesTransport`.**

```
Harness process
  HermesBridge -> HermesProtocolAdapter -> HermesTransport (pipe / ws)
                                                       |
                                          Termux process (separate UID)
                                            python 3.14.6
                                            hermes tui_gateway (stdio / ws)
```

The existing M0-006 abstraction is unchanged and already correct: this needs a
concrete `TermuxTransport`, not a redesign. `HermesRuntime` keeps its stable
shape with `TermuxRuntimeBackend` as its first concrete implementation.

Rationale, strictly from the evidence above:
1. Hermes requires Python >= 3.14 for 45 core deps; Termux ships 3.14.6 aarch64,
   in-process Android Python does not reach 3.14 at all.
2. Hermes declares `[termux]` extras and fixes Termux installs upstream —
   Android is a supported target at this commit.
3. The native dependency closure builds from sdist in Termux's userland;
   Chaquopy consumes prebuilt ABI wheels that do not exist for Android.
4. Hermes' subprocess-heavy design needs a real userland.

`EmbeddedPythonRuntimeBackend` is **retained in the abstraction but rejected as
an implementation** for this revision. The interface stays so the decision can
be revisited if upstream relaxes the Python floor.

## 13. What must be implemented next (M0-008)

1. `TermuxTransport`: pipe + newline-delimited JSON over the gateway stdio path.
2. `TermuxRuntimeBackend` implementing `HermesRuntime`: start, health, readiness,
   reconnect, shutdown, crash detection, log capture.
3. Runtime-lifecycle tests against `FakeHermesTransport` — **not** fakes dressed
   as integration.
4. Feed bridge events into the existing `EventBus` (still CoT-filtered).
5. Route server->client requests to `CapabilityManager`; never auto-answer.
6. Wake-lock / foreground-service handling for Termux residency.
7. Re-audit upstream before any version bump.

## 14. What must NOT be implemented yet

- No Chaquopy / embedded Python / vendored Python.
- No Hermes packaging into the APK.
- No MCP integration beyond what the gateway already speaks.
- No plugin or skills installation.
- No advanced Android capabilities (root, Device Owner, Accessibility,
  MediaProjection, VPN).
- No M1.
- No claim that Hermes runs on the device until it has actually been started.

## Evidence appendix

- `pyproject.toml`, `.python-version`, `.nvmrc` @ `eaecc99c`
- Termux `termux-main` pool listing (aarch64): `python_3.14.6-1`,
  `nodejs_26.4.0-1`
- PyPI JSON API per-package, at exact upstream pins, counting `android` /
  `manylinux_*_aarch64` / sdist artefacts
- Upstream PR #100574 "select a supported Python on Termux, with TUR fallback"
- adb on `b36d068a`: API 36, arm64-v8a, SELinux Enforcing, 170 GB free,
  `com.termux` absent, no python/node on PATH
- Full protocol audit: `docs/HERMES_UPSTREAM_AUDIT_M0-006.md`