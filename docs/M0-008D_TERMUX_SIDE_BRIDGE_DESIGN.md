# M0-008D Termux-Side Bridge Design

## 1. Problem

M0-008C concluded **B**: cross-UID Binder + `ParcelFileDescriptor` transfer works
(proven on device), but stock Termux exposes nothing that yields a *live*
process stream. This milestone asks whether a Termux-side component can supply
one, and what it would take to build it.

## 2. M0-008C findings carried forward

- Harness uid 10379 received a valid FD from Termux uid 10361 — platform works.
- `TermuxOpenReceiver$ContentProvider.openFile()` returns regular files;
  `openPipeHelper` / `createPipe` / `createSocketPair` are all absent.
- `TermuxService` is `exported=false` and carries no `ParcelFileDescriptor`.

## 3. Termux source findings — the decisive one

Read from the **installed APK manifest** and from Termux' own plugin repos:

```
AndroidManifest.xml (com.termux, v0.118.3)
  android:sharedUserId = "com.termux"
```

On the device:
```
sharedUser=SharedUserSetting{e7ad547 com.termux/10361}
```

And Termux' own official plugins declare the same shared UID:

| Plugin | sharedUserId |
|---|---|
| termux-boot | `com.termux` |
| termux-api | `${TERMUX_PACKAGE_NAME}` -> `com.termux` |
| termux-tasker | `${TERMUX_PACKAGE_NAME}` -> `com.termux` |

**This settles the architecture question.** Termux' plugin model is not built on
intent-passing alone — plugins are installed into Termux' shared UID and run
inside Termux' sandbox. Termux:API can therefore exec Termux binaries; that is
precisely the property this project needs.

## 4. Candidate architectures

| # | Form | Assessment |
|---|---|---|
| A | Patch Termux to export a Binder service | Works, but ships a **non-stock Termux**. |
| B | **Termux plugin sharing Termux' UID** | **Architecture viable** — proven by Termux' own plugins. Blocked on signing, not design. |
| C | Separate APK, own UID | Dead: the UID wall again (M0-008A/B). |

## 5. Selected architecture

A Termux plugin that joins the shared UID, exposes an exported,
permission-guarded Binder service, and hands the Harness transferred file
descriptors rather than bulk bytes.

**Binder is used for control, not for bulk bytes.** Binder transactions are
message-oriented, size-limited and add per-call overhead. The established
Android pattern is Binder to *establish* the channel, then
`ParcelFileDescriptor` for the actual bytes.

## 6. Binder contract (structured, never a shell)

```
createSession(config) -> sessionId + stdinFd + stdoutFd + stderrFd
writeStdin(sessionId, byte[])
closeSession(sessionId, reason)
sessionState(sessionId) -> CREATED|STARTING|RUNNING|STOPPING|STOPPED|FAILED
```

There is deliberately **no** `execute(String command)`. The command comes from
trusted configuration in the plugin, never from a caller and never from an LLM.

## 7. FD transport design

`ParcelFileDescriptor.createPipe()` is **unidirectional** and must not be
treated as duplex. Hermes needs three distinct directions, so the design uses:

```
Harness stdin  --[pipe]-->  bridge  -->  child stdin
child stdout  --[pipe]-->  bridge  -->  Harness stdout
child stderr  --[pipe]-->  bridge  -->  Harness stderr
```

Three unidirectional channels, each live for the child's lifetime. No file
polling, no repeated `RUN_COMMAND`, no clipboard, no log scraping.

## 8. Authorization model

- Exported service guarded by an explicit custom permission
  (`protectionLevel=dangerous`), mirroring Termux' own `RUN_COMMAND` pattern.
- Caller identity resolved by the framework; the bridge re-checks the calling
  UID/package before doing anything.
- Per-session token bound at creation, so a captured session id is useless alone.
- Rejection happens **before** any child is spawned.
- `android:sharedUserId` is itself a second gate: only apps signed with Termux'
  certificate can even be installed into that UID.

## 9. Process ownership

Explicit and unchanged: `ProcessManager` supervises **Android-owned** processes
only. A Termux-owned child is represented as a **remote session** owned by the
plugin. `ProcessManager` was not modified.

## 10. Session model

`BridgeSessionId` is distinct from Android `taskId`, Hermes `session_id`, Hermes
`stored_session_id` and the JSON-RPC correlation id. A session carries a unique
id, caller identity, process handle, creation time, lifecycle state and
termination reason.

## 11. Lifecycle

CREATED -> STARTING -> RUNNING -> STOPPING -> STOPPED, plus FAILED. Handles
client disconnect, child exit, service death, duplicate close, startup failure
and timeout. Reconnect semantics are **not claimed** - they are not proven.

## 12. Physical-device experiment

**Not performed, and deliberately not faked.** Proving it requires an APK signed
with Termux' release certificate, which this project does not possess. Shipping a
re-signed Termux to make the test pass would have produced a misleading result on
a device whose real Termux was untouched.

What *was* verified on the device: Termux runs as shared UID 10361; the shared
UID is declared in the shipped manifest; cross-UID FD transfer works (M0-008C).

## 13. Security analysis

No root, no Shizuku, no unrestricted exported component, no loopback bypass, no
shared-storage workaround, no shell bypass, no arbitrary filesystem exposure.
`ProcessManager` untouched. The bridge rejects unauthorized callers before
spawning.

## 14. Distribution implications

An architecture note, not a policy conclusion.

- **Stock Termux: insufficient.** No streaming interface is exported.
- **Modified Termux:** needs a re-signed build, losing the official signature
  and therefore store distribution.
- **Official-style Termux plugin:** the viable route, but it must be signed with
  Termux' certificate, so it has to ship from the Termux project - or the user
  must install a self-signed Termux variant.
- No App Store / Play policy claim is made; that was not verified from
  authoritative current sources.

## 15. Hermes compatibility

Not tested. The transport shape - three unidirectional FDs - is the right shape
for `gateway.ready` plus newline-delimited JSON. Carrying 200 kB frames was
demonstrated over the loopback relay in M0-008B, but that path is now unusable.

## 16. Remaining blockers

1. **Signing.** No Termux-keyed plugin can be produced by this project.
2. The plugin itself is unbuilt and untested on-device.
3. Background/lifecycle behaviour of a shared-UID service is unmeasured.

## Classification

- **VERIFIED FROM SOURCE**: `sharedUserId=com.termux` in the Termux manifest;
  the same declaration in termux-boot/api/tasker; `openPipeHelper` absent from
  Termux' provider.
- **OBSERVED ON DEVICE**: `sharedUser=SharedUserSetting{... com.termux/10361}`;
  Termux processes running as uid 10361; cross-UID FD transfer succeeding.
- **INFERRED**: that a shared-UID plugin can exec Hermes and relay its streams.
  Strongly supported by Termux' own plugins, but not executed here.
- **UNKNOWN**: whether the Termux project would accept such a plugin; measured
  background behaviour.
- **BLOCKED**: prototype and device proof, pending a signing path.