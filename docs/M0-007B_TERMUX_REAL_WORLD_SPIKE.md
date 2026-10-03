# M0-007B Termux Real-World Feasibility Spike

**Result: PROVEN FEASIBLE.**

Hermes `@ eaecc99c` was installed, booted and answered JSON-RPC on the physical
device. The evidence M0-007 lacked now exists.

Scope note: this document records a spike. No production runtime code was
written, the Harness app was not modified, and the Android app never talks to
Termux yet.

## 1. Device

| Property | Value |
|---|---|
| Model | 24069RA21C (Xiaomi Redmi) |
| Android | 16 |
| API | 36 |
| ABI | arm64-v8a |
| Kernel | 6.1.138-android14-11, aarch64 |
| SELinux | Enforcing |
| Storage free | 166 GB of 227 GB |
| ADB | connected, authorised |

## 2. Termux version / source

| Property | Value |
|---|---|
| Package | `com.termux` |
| Version | 0.118.3 (`versionCode` 1002) |
| Source | official GitHub release, `termux/termux-app` tag `v0.118.3` |
| Asset | `termux-app_v0.118.3+github-debug_arm64-v8a.apk` |
| **SHA-256** | `72fdb596045116bf5ba1b5bdf5b26fddb9acc0bd074ad9f2da9eb0ae85e83a4e` |
| Checksum source | the release's own `sha256sums` asset — **verified match** |
| minSdk / targetSdk | 24 / 28 |
| Package repo | `https://packages-cf.termux.dev/apt/termux-main/` (official) |

Not a random APK and not a third-party mirror: the digest was taken from the
upstream release manifest and matched byte-for-byte before install.

## 3. Python result — PROVEN

```
python -VV   -> Python 3.14.6 (main, Jul  5 2026) [Clang 21.0.0]
machine      -> aarch64
system       -> Android
pip          -> 26.2.1 (venv) / 26.1.2 (pip-installed)
venv         -> module OK, venv created successfully
```

Hermes requires `>=3.11,<3.15` and gates all 45 core dependencies on
`python_version >= '3.14'`. **3.14.6 satisfies both.** This closes the M0-007
inference that the interpreter would be adequate.

## 4. Node result — PROVEN, with a real obstacle

```
node --version -> v26.4.0     (.nvmrc requires 26)
npm  --version -> 11.20.0
process.arch   -> arm64
```

**Obstacle found and fixed:** Node 26.4.0 initially failed to link:

```
CANNOT LINK EXECUTABLE "node": cannot locate symbol
"OSSL_PROVIDER_add_conf_parameter"
```

Cause: the base image shipped OpenSSL **3.4.1**, which predates that symbol.
Fix: upgrade OpenSSL to **3.6.5** from the official Termux repo. This is a
packaging-provisioning step, not a requirement being weakened.

Two further non-technical obstacles, both resolved:
- `apt` aborted on a conffile prompt (`openssl.cnf`) when stdin was not a tty.
  Resolved with `-o Dpkg::Options::=--force-confnew`.
- Git was absent; `git 2.56.0` installed.

## 5. Hermes commit verification — PROVEN

```
$ git rev-parse HEAD
eaecc99c7ec5b6f37e880a0b69d16871cd3e4f57
```
Exact match with the audited pin. Confirmed on-device:
`requires-python = ">=3.11,<3.15"`, `.python-version` = `3.14`,
`.nvmrc` = `26`.

## 6. Dependency installation — PROVEN, after 3 real blockers

Command (the real extras from `pyproject.toml`, unmodified):
```
hv/bin/pip install -e '.[termux]'
```

Final result:
```
Successfully built hermes-agent pillow-heif
Successfully installed  (78 packages) including:
  openai-2.24.0  pydantic-2.13.4  httpx-0.28.1  cryptography-50.0.1
  mcp-2.0.0  firecrawl-anydoc-0.2.4  resvg-py-0.4.0  psutil-8.0.0
  Pillow-12.3.0  pillow-heif-1.8.0  prompt_toolkit-3.0.52  websockets-15.0.1
Successfully installed hermes-agent-0.0.0
```

`import openai` -> `2.24.0`; `import pydantic` OK; `hermes` CLI present in
`hv/bin`.

### Blockers hit, in order (all resolved without weakening requirements)

| # | Blocker | Root cause | Resolution |
|---|---|---|---|
| 1 | `firecrawl-anydoc` metadata failed | needs `maturin`; `rustup` lacks target `aarch64-unknown-linux-android` | installed Termux `rust 1.98.1`, which ships `rust-std-aarch64-linux-android` |
| 2 | `Pillow` wheel build failed | missing jpeg/png headers | `apt install libjpeg-turbo libpng libwebp freetype` |
| 3 | `pillow-heif` wheel build failed | missing libheif headers | `apt install libheif` (1.23.5) |

Native wheels produced **on device** for Android:
```
psutil-8.0.0-cp38-abi3-android_24_arm64_v8a.whl
cryptography-50.0.1-cp314-abi3-android_24_arm64_v8a.whl
httptools-0.8.0-cp314-cp314-android_24_arm64_v8a.whl
tornado-6.5.10-cp39-abi3-android_24_arm64_v8a.whl
watchfiles-1.3.0-cp310-abi3-android_24_arm64_v8a.whl
rpds_py-2026.6.3-cp314-cp314-android_24_arm64_v8a.whl
```

None of these ship Android wheels on PyPI. M0-007 predicted they would have to
be built from sdist in Termux's userland — **observed, not inferred**.

### Cost

| Item | Size |
|---|---|
| hermes checkout | 342 MB |
| venv | 148 MB |
| whole Termux prefix after install | 3.2 GB |

Wall-clock: roughly 25 minutes of mostly sdist/Rust compilation across three
install attempts. First-time install is not fast, but it completes.

## 7. Native / build requirements

Required on-device: `clang 21.1.8`, `llvm`, `lld`, `make 4.4.1`,
`rust 1.98.1` + `rust-std-aarch64-linux-android`, `git 2.56.0`,
`pkg-config`, plus headers `libjpeg-turbo`, `libpng`, `libwebp`, `freetype`,
`libheif`. No missing shared libraries were encountered after these were
present. `faster-whisper` (voice extra only) was never reached and remains
unavailable — it does not affect the gateway.

## 8. SQLite / WAL — PROVEN on device

M0-007 flagged WAL as UNKNOWN. Now measured:

```
sqlite3 lib version : 3.53.4
platform            : Android-16-aarch64-64bit-ELF
default journal_mode: delete
after PRAGMA journal_mode=WAL : wal
CREATE + INSERT + SELECT -> rowcount 1
```

And from a real Hermes run, the sidecars genuinely exist:
```
/data/data/com.termux/files/home/.hermes/state.db
/data/data/com.termux/files/home/.hermes/state.db-wal
/data/data/com.termux/files/home/.hermes/state.db-shm
```

**WAL was not disabled to make anything pass.** Hermes created its own
`state.db-wal`/`-shm` unprompted.

Residual UNKNOWN: behaviour of WAL sidecars under memory pressure or
backup/restore. Not exercised here.

## 9. Gateway startup command

Not guessed. Read from the audited source: `pyproject.toml` declares
`hermes = "hermes_cli.main:main"`; `hermes --tui` spawns `tui_gateway.entry`
(`hermes_cli/dashboard_procs.py`). The gateway was started directly:

```
PYTHONUNBUFFERED=1 hv/bin/python -m tui_gateway.entry
```

## 10. `gateway.ready` — PROVEN

Real captured stdout, on device:
```
{"jsonrpc": "2.0", "method": "event", "params": {"type": "gateway.ready",
 "payload": {"skin": {"name": "default", ...}, "change_events": true,
 "replay_epoch": "2eebe168e90646a6867a632346201aeb"}}}
```

Readiness time: **1.37–1.44 s** across runs.

### Framing — PROVEN

```
starts with          : {"jsonrpc": "2.0", "method": "event", ...
contains Content-Length : False
newline terminated   : True
parses as ONE JSON object on ONE line : True
top-level keys       : ['jsonrpc', 'method', 'params']
params keys          : ['payload', 'type']
```

Newline-delimited JSON, exactly as the M0-006 audit concluded. **No
Content-Length framing.** The M0-006 `HermesFraming` splitter is correct.

One correction to the audit: on the **stdio** path the event frame carries the
event name in `params.type`, not `params.method`. M0-006's decoder treats a
frame with no `method` and no id as `Ignored`. A real gateway frame therefore
needs the `params.type` form handled. This is a genuine gap found only by
running the real gateway.

## 11. JSON-RPC proof

All against the real gateway, no credentials, no secrets, no tool execution.

| Call | Result |
|---|---|
| `gateway.ready` | event received |
| `session.create` | `{"session_id": "912aebaf", "stored_session_id": "20261003_102741_a3be3b", "message_count": 0}` |
| `session.interrupt` (bogus id) | error `4001 session not found` |
| `not.a.real.method` | error `-32601 unknown method` — version-skew message |
| `session.status` (bogus id) | error `4001 session not found` |

`prompt.submit` was **not** sent: it would require a configured model provider
and real credentials. Deliberately not attempted.

**Correction to the audit:** `gateway.ping` is answered `-32601 unknown method`
on the stdio path. It exists only in `tui_gateway/ws.py` (WebSocket). M0-006's
`connect()` sends `gateway.ping` as a liveness probe; against a stdio gateway
that probe is invalid. M0-006 needs to make the probe transport-aware.

## 12. Session-id observations

Two ids, genuinely distinct, exactly as the audit flagged:

```
session_id       : 912aebaf                      (live runtime id, 8 hex chars)
stored_session_id: 20261003_102741_a3be3b        (durable, timestamp-prefixed)
IDS DISTINCT     : True
```

`session.info` also reported `"branch": "eaecc99c"` — the gateway is running
the pinned revision.

The M0-006 `HermesSessionRef` split (`runtimeSessionId` vs `storedSessionId`)
matches observed reality. `session.resume` semantics were not exercised (it
needs a stored session with history), so that half remains INFERRED.

## 13. Lifecycle observations

| Measurement | Result |
|---|---|
| Gateway startup | ~1.4 s to `gateway.ready` |
| Process survives while stdin held open | yes (verified after 3 s) |
| Exit on stdin EOF | `[gateway-exit] stdin EOF`, exit code 0 — clean |
| Restart | works; a fresh `replay_epoch` each start |
| Backgrounded / screen off | **NOT MEASURED** — UNKNOWN |
| Android killing the process | **NOT OBSERVED**, but also not stress-tested |
| Wake-lock tooling | `termux-wake-lock` / `termux-wake-unlock` present |

Important nuance: the one observed process death was **not** Android killing
it. The log shows `[gateway-exit] stdin EOF` — the gateway exits when its
stdin closes, by design. Android-kill behaviour under memory pressure remains
**UNKNOWN** and must be measured before relying on residency.

## 14. Security observations

- Termux runs as its own UID (`u0_a361`), a sandbox separate from the Harness
  app. A Hermes compromise does not grant Harness memory.
- Hermes runs as an external process; the Harness never loads Hermes code.
- No credentials were configured; no API key was set; no prompt was submitted.
- The Harness app was **not modified**: `git diff develop..branch` touches no
  policy, workspace, event or router file.
- `WorkspaceBroker`, `CapabilityManager`, `TrustedPolicyEngine` and
  `ProcessManager` are untouched and unexercised by this spike.
- No Accessibility, MediaProjection, Device Owner or root was used.
- Only `adb install` (standard app install) and `run-as com.termux` (permitted
  because the GitHub Termux build is debuggable) were used to reach the
  Termux userland. The Harness app itself was never involved.

## 15. Exact blockers remaining

1. **`gateway.ping` is invalid on stdio** (only in `ws.py`). M0-006's
   `connect()` probe must be transport-aware.
2. **Event name location**: stdio frames put the event name in `params.type`.
   M0-006's decoder must handle that form.
3. **`prompt.submit` unexercised** — needs a provider and credentials. Cannot
   be done safely in a spike.
4. **Android-kill / residency UNKNOWN** — needs a wake lock plus a real
   memory-pressure and screen-off test.
5. **First install ~25 min and 3.2 GB.** Acceptable for a developer install,
   unacceptable as a silent first-run experience; the Harness must not attempt
   it implicitly.
6. **OpenSSL upgrade is a mandatory provisioning step**, not optional — Node
   26 will not link without it.

## 16. FEASIBILITY RESULT

### PROVEN FEASIBLE

Justified, not asserted. The following were **actually executed on the physical
device**: Termux installed from a checksum-verified official build; Python
3.14.6 and Node 26.4.0 provisioned; Hermes `@ eaecc99c` cloned and its commit
verified on-device; the real `[termux]` dependency closure installed after
three native-build blockers were fixed; 78 packages including the full LLM
client and MCP stack; the real gateway booted; `gateway.ready` observed;
newline framing confirmed; `session.create` returning two distinct session
ids; SQLite WAL confirmed with live sidecars.

This upgrades M0-007's Termux verdict from INFERRED to OBSERVED.

**What is still NOT proven:** that Hermes completes an actual agent turn, since
that needs a configured model provider and credentials; and that the gateway
survives Android memory pressure.

**Do we have enough evidence to begin the production `TermuxRuntimeBackend`
milestone?** **Yes** — the blockers that mattered were environmental, and all
three are resolved with documented steps. But the production milestone must
first absorb findings #1 and #2 above, because M0-006's current bridge would
misread a real stdio gateway. Those are protocol corrections, not runtime work.