# M0-008G Runtime Strategy Pivot

## Headline

The Termux external-plugin path is **FROZEN**. Re-auditing the embedded-Python
path produced a result that **reverses a conclusion from M0-006**.

---

## 1. Why the Termux external-plugin path is frozen

**OBSERVED ON DEVICE (M0-008F):**

```
package:dev.vitngan.termuxbridge uid:10384
package:com.termux              uid:10361
```

A separately installed plugin, declaring `android:sharedUserId="com.termux"`
and signed with a certificate **bit-identical** to Termux, was still allocated
its own appId. The signing match is real; the shared-UID membership is not.

**INFERRED:** on this Android 16 build, `android:sharedUserId` no longer admits
a third-party package into an existing shared UID.

**VERIFIED FROM SOURCE:** M0-008E's analysis that a matching signer is
*sufficient* to join the shared UID is therefore **obsolete**. M0-008F is the
authoritative result for this path. M0-008E is retained as historical evidence
and is explicitly **superseded**; history is not rewritten.

`ProcessManager` was not weakened. No root, Shizuku or SELinux bypass is used or
proposed.

---

## 2. Authoritative evidence carried forward

| Fact | Class |
|---|---|
| Plugin installs successfully | OBSERVED |
| Plugin signer SHA-256 equals Termux's | VERIFIED |
| Plugin UID != Termux UID | OBSERVED |
| `sharedUserId` ignored for 3rd-party package | INFERRED |
| Stock-Termux + external plugin is invalid | BLOCKED |

---

## 3. Candidate A - Embedded Python (Chaquopy)

### This reverses M0-006

M0-006 assumed Chaquopy caps at Python 3.13, making Hermes's
`requires-python = ">=3.14"` unsatisfiable and gating all 45 dependencies.
**That assumption was wrong.**

**VERIFIED (vendor documentation, Chaquopy 17.0 version summary):**

| Chaquopy | Python versions |
|---|---|
| **17.0** | **3.10 - 3.14** |
| 16.1 | 3.8 - 3.13 |

**VERIFIED:** upstream issue chaquo/chaquopy#1350 "Add support for Python
version 3.14" is **CLOSED** (via #1428), milestone 17.0.

**VERIFIED:** `com.chaquo.python:gradle:17.0.0` is present in Maven Central
`maven-metadata.xml`.

### Consequence

The Python-version gate is **SATISFIABLE**. The dominant remaining risk is no
longer the interpreter version. It is **native extension ABI**.

### Native extension gate

Hermes's dependency closure contains many packages with C extensions
(`pydantic-core`, `numpy`, `cryptography`, `orjson`, `charset-normalizer`,
`pydantic-settings`, `httpx`/`httpcore`/`h11`, `websockets`, `jiter`).
Chaquopy builds these from recipes for `arm64-v8a`. Each is an **INFERRED**
per-package risk: a recipe may exist, may need patching, or may have no
upstream Android-compatible recipe at all.

### Classification

**PARTIALLY FEASIBLE.** Not `PROVEN`: no native package has been built for
Android in this project. Not `NOT FEASIBLE`: the interpreter gate is cleared
and Chaquopy exists specifically to solve this.

---

## 4. Candidate B - Custom Python + native sidecar

A Harness-owned sidecar process running bundled CPython.

Android places no extra restriction on an app spawning a second app-owned
process; the same UID, same sandbox, same `ProcessManager` policy applies.
A sidecar therefore needs **no** shared UID at all, which is precisely the
mechanism M0-008F proved unavailable.

**UNKNOWN:** Chaquopy loads CPython **in-process** via JNI. Extracting it into
a standalone sidecar process is not a supported Chaquopy configuration and
would require a custom CPython Android build. No such build exists here.

**Classification: PARTIALLY FEASIBLE**, strictly a superset of Candidate A's
native risk plus an unsupported extraction step.

---

## 5. Candidate C - Userland Linux container

**NOT FEASIBLE** on the evidence available. A meaningful container needs
`clone`/`CLONE_NEWUSER`/`CLONE_NEWNS`, `pivot_root`, a writable overlay and
usually cgroup control. Android's kernel omits user namespaces on the mainline
config, and the app sandbox forbids the mount operations. Executing userspace
binaries additionally requires `execve` of foreign-ABI ELF, which the W^X and
noexec restrictions of the app data partition block.

**INFERRED** from Android platform architecture; **no experiment was run**.
Per the milestone instruction, no generic container is assumed to work.

**Classification: NOT FEASIBLE** under no-root, no-Shizuku constraints.

---

## 6. Candidate D - Remote Hermes runtime

**VERIFIED FROM SOURCE (M0-006):** upstream `hermes_cli/transport/ws.py` and
`hermes_cli/websocket_server.py` implement a WebSocket control protocol with
JSON stdio frames. The protocol half already exists.

**Required for the client side (not yet written):** reconnect, session
persistence, TLS, authentication, LAN discovery.

**Honest restatement:** this changes the product from *"Hermes on Android"* to
*"Android Harness controlling remote Hermes"*. Offline capability is **lost**.
The remote host owns the workspace, credentials and child processes, so the
Android security boundary no longer contains them.

**Classification: TECHNICALLY VIABLE**, changes the product, no offline.

---

## 7. Candidate E - Custom Termux distribution

Rebuilding Termux with the bridge compiled in would solve all three points,
because it would share Termux's `applicationId` and run in its own process -
no shared UID needed.

**Classification: TECHNICALLY VIABLE, but PRODUCT CURRENTLY EXCLUDES IT.**
Not presented as the selected path.

---

## 8. Candidate F - Upstream Termux contribution

**Classification: POSSIBLE** technically. Requires a first-class API, a
permission model, lifecycle ownership and stream transport in Termux itself.
Acceptance is out of scope and is not speculated about.

---

## 9. Comparison

| Architecture | Hermes unchanged | On-device | Offline | Persistent stdio | Process isolation | Distribution complexity | Status |
|---|---|---|---|---|---|---|---|
| A Embedded Python | Yes, if wheels build | Yes | Yes | Yes, in-process | None (same UID) | Large APK | PARTIALLY FEASIBLE |
| B Native sidecar | Yes, if extraction works | Yes | Yes | Yes, across FDs | Process boundary | Large APK + custom CPython | PARTIALLY FEASIBLE |
| C Userland container | Yes | Yes | Yes | Yes | Yes | Very large | NOT FEASIBLE |
| D Remote runtime | Yes | No | No | Yes, over WS | Host-owned | Small APK | TECHNICALLY VIABLE |
| E Custom Termux build | Yes | Yes | Yes | Yes | Termux-owned | Custom build chain | TECHNICALLY VIABLE, product excludes |
| F Upstream Termux | Yes | Yes | Yes | Yes | Termux-owned | Depends on upstream | POSSIBLE |

No ranking. No scoring.

---

## 10. Security comparison

| Control | A | B | C | D | E/F |
|---|---|---|---|---|---|
| Process ownership | App UID | App UID | App UID | Remote host | Termux UID |
| Caller authorization | ProcessManager policy | ProcessManager policy | n/a | TLS + token | Termux plugin model |
| Workspace confinement | App sandbox | App sandbox | App sandbox | **Remote host** | Termux sandbox |
| Credentials | On device | On device | On device | **Off device** | On device |
| Network boundary | None needed | None needed | None needed | **New attack surface** | None needed |
| Capability authority | None | None | None | Remote authority | Termux |
| Filesystem access | Scoped | Scoped | Scoped | **Host-wide** | Termux scope |
| Prompt-injection containment | In-process boundary | Process boundary | Container boundary | **Crosses network** | Termux boundary |

All architectures preserve: **LLM output is instruction/data, never security
authority.**

---

## 11. Physical-device evidence

Device: Xiaomi Redmi 24069RA21C, Android 16 / API 36, arm64-v8a.

| Claim | Tested on device |
|---|---|
| Cross-UID Binder works | Yes (M0-008C) |
| `ParcelFileDescriptor` transfer works | Yes (M0-008C) |
| Plugin installs and runs | Yes (M0-008F) |
| Plugin joins Termux UID | **Yes - it does NOT** |
| Chaquopy Python 3.14 on device | **Not tested** |
| Any Hermes dependency wheel on device | **Not tested** |

---

## 12. Remaining unknowns

1. Does every native Hermes dependency have an Android arm64 Chaquopy recipe?
2. Does the full wheel set install and import under Python 3.14?
3. Can CPython be extracted from Chaquopy into a sidecar process at all?
4. APK size at acceptable limits?
5. SQLite/WAL and `subprocess` behaviour under Android's sandbox.

---

## 13. Technical recommendation

The only on-device architecture still technically viable is **Candidate A**
(embedded Python via Chaquopy 17.0), with **Candidate B** as a later refinement
if process isolation proves necessary.

The single cheapest experiment that separates *"plausible"* from *"actually
works"* is: **install Chaquopy 17.0 with Python 3.14 and resolve Hermes's full
dependency closure on the device, measuring APK size and import failures.** This
requires no Hermes modification and no production code.

---

## 14. Product decisions still required

The project owner must decide, not the code:

- Accept a substantially larger APK?
- Accept losing offline capability if a remote runtime is chosen?
- Accept a modified runtime distribution or companion runtime?
- Which is acceptable: APK size, offline loss, or neither?

---

## Stop condition answers

**A.** Candidate A (embedded Python, Chaquopy 17.0) is the only on-device
architecture still technically viable. Candidate B is a superset.

**B.** Candidate A is testable next, via a dependency-closure resolution
experiment. It is the cheapest and it is falsifiable.

**C.** Hermes **can** remain completely unchanged for Candidates A, B, D, E and
F. Chaquopy is an interpreter-supply mechanism, not a fork requirement. This was
not previously established.

**D.** The unavoidable tradeoff is **APK size against the dependency closure**,
and for Candidate A specifically, accepting **in-process** execution, meaning no
process isolation boundary around Hermes.

**E.** Next milestone would be a throwaway dependency-closure probe - **not**
a runtime integration. No `TermuxRuntimeBackend`, no transport, no embedding.

---

## Corrections recorded

- **M0-006** claimed Chaquopy capped at Python 3.13, gating all 45
  dependencies. **Incorrect.** Chaquopy 17.0 supports 3.10-3.14.
- **M0-008E** claimed a matching signer makes shared-UID membership deployable.
  **Incorrect**, per M0-008F.

Both corrections are recorded rather than left standing. History is not
rewritten.
