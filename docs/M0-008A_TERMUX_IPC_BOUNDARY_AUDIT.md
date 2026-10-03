# M0-008A Termux IPC Boundary Audit

## 1. Problem statement

The Android Harness needs to host the Hermes stdio gateway, which requires a
**long-lived, bidirectional** process channel: start it, read `gateway.ready`,
then exchange newline-delimited JSON in both directions continuously.

M0-008 proved `ProcessManager.launchSupervised()` can supervise a bidirectional
child. But the child in question runs inside Termux' sandbox, which the Harness
cannot reach. This audit determines what IPC, if any, closes that gap.

## 2. Physical evidence (device: 24069RA21C, Android 16, API 36, arm64-v8a)

| Check | Result |
|---|---|
| Harness reads Termux sandbox (`ls /data/data/com.termux/files/usr/bin`) | **Permission denied** |
| Harness execs Termux python | **Permission denied** |
| Termux `termux-service` binary | **absent** in 0.118.3 |
| Termux process | present (pid 27197) |
| SELinux | Enforcing |

This is a per-UID Android sandbox boundary. It is not a bug to be worked around.

## 3. Termux 0.118.3 capabilities (VERIFIED FROM SOURCE)

Audited `termux/termux-app` at tag **v0.118.3** (`5b657c6adf4304e5198951ce815fe0205dcac29c`).

Manifest actions registered by the installed build:
`MAIN`, `SEND`, `VIEW`, `DOCUMENTS_PROVIDER`, **`com.termux.RUN_COMMAND`**.

Exactly **one** callable service:
```
com.termux.RUN_COMMAND -> com.termux/.app.RunCommandService
                          guard: com.termux.permission.RUN_COMMAND
```

Three independent gates, all observed on the device:

1. **Android permission** `com.termux.permission.RUN_COMMAND`, `prot=dangerous`.
   Declaring it is not enough; the user must grant it. We granted it and
   confirmed `granted=true`.
2. **Termux-side switch** `allow-external-apps = true` in
   `~/.termux/termux.properties`. Termux' own log:
   ```
   E Termux:RunCommandService: RunCommandService requires `allow-external-apps`
   property to be set to `true` in `~/.termux/termux.properties` file.
   ```
   Default is **off**, deliberately: the shipped file says *"Allow external
   applications to execute arbitrary commands within Termux. This potentially
   could be a security issue, so option is disabled by default."*
3. **Mandatory extra** `com.termux.RUN_COMMAND_PATH` - an absolute path to an
   executable inside Termux' own sandbox. Observed:
   ```
   E Termux:RunCommandService: Mandatory extra missing to
   RunCommandService: "com.termux.RUN_COMMAND_PATH"
   ```

### The decisive finding: no streaming surface

From `RunCommandService.java` at v0.118.3:
- result delivery is `EXTRA_PENDING_INTENT` plus an optional
  `EXTRA_RESULT_DIRECTORY` / `EXTRA_RESULT_SINGLE_FILE` **file**;
- `EXTRA_STDIN` is read as a **single String** and written to the child once;
- searching the file for `Socket`, `ServerSocket`, `ParcelFileDescriptor`,
  `AIDL`, `Messenger`, `bind` returns **nothing**.

`RUN_COMMAND` is therefore strictly **request / response**. It is not a stream,
and no amount of configuration makes it one.

## 4. Candidate mechanisms

| Mechanism | Supported by 0.118.3 | Bidirectional stream | Verdict |
|---|---|---|---|
| Direct `ProcessBuilder` into Termux | n/a | would be yes | **BLOCKED** by SELinux/UID |
| `RunCommandService` (`RUN_COMMAND`) | yes, 3 gates | **no** - one-shot | **PROVISIONING ONLY** |
| Termux:API (`com.termux.api`) | **not installed** | no - one-shot result | rejected |
| Shizuku | installed, **service not running** | would be yes (uid 2000) | rejected: privilege escalation, needs user-driven wireless ADB |
| Termux plugin APK | not installed | would be yes | rejected: nothing to build against |
| Custom helper APK | n/a | would be yes | **the missing piece** - see 10 |

Shizuku deserves a note: it is installed (`moe.shizuku.privileged.api`) but its
service is **not running**, and adopting it would mean granting the Harness
shell-level access to every app on the device. That is a far larger privilege
than "run my agent", so it is rejected on the security requirement, not on
capability.

## 5. Security analysis

`ProcessManager` and the Termux bridge are **different things** and must not be
merged:

- `ProcessManager.launchSupervised()` controls processes the Harness is itself
  authorised and technically able to launch.
- A Termux IPC bridge controls processes inside **someone else's** sandbox.

Forcing Termux through `ProcessBuilder` would mean weakening the path/token
model to accommodate a binary the Harness cannot execute - exactly the wrong
direction. So the boundary stays separate:

```
TermuxRuntimeBackend -> TermuxExecutionBridge -> [IPC] -> Termux-side -> Hermes
```

The IPC channel is a **request channel, not a data channel**. It grants no
filesystem access: the Harness still cannot read `/data/data/com.termux`
(observed). Workspace authorisation stays an Android concern; Termux execution
stays separately sandboxed. `allow-external-apps` is itself a privilege the
*user* grants to *Termux*, not something the Harness takes.

## 6. Lifecycle analysis

`RUN_COMMAND` can start a long-lived process (`EXTRA_BACKGROUND=true`, and
Termux raises a foreground-service notification - observed in logcat as
`RUN_COMMAND_NOTIFICATION_CHANNEL`). It cannot, however, observe that process's
lifetime from outside, and it cannot terminate it. Both were verified as
*absences in source*, not assumed.

## 7. Streaming analysis

| Requirement | `RUN_COMMAND` |
|---|---|
| start a process | yes |
| keep alive | yes (background) |
| receive `gateway.ready` | only as a completed one-shot result, at best |
| continuous bidirectional JSON | **no** |
| feed stdin after start | **no** - stdin is a single string sent once |
| observe exit | **no** |
| terminate | **no** |

Conclusion: `RUN_COMMAND` cannot host the Hermes stdio gateway. It can
provision one - "install and prepare the runtime, tell me when you are done".

## 8. Selected mechanism

**Provisioning: `com.termux.RUN_COMMAND`.** Accepted as a one-shot provisioning
channel, behind all three gates, with results delivered by `PendingIntent`.

**Runtime stdio transport: none available.** No mechanism in Termux 0.118.3
provides the bidirectional channel the gateway needs.

## 9. Rejected mechanisms, and why

- **Direct `ProcessBuilder` into Termux** - blocked by the per-UID sandbox.
  Verified, not assumed.
- **`sh -c` / shell tricks / filesystem hacks** - explicitly out of scope, and
  would be a security regression anyway.
- **Shizuku** - would work technically but grants shell-level access to every
  app. Disproportionate to the requirement.
- **Polling a result file and calling it stdin** - this is the fake-streaming
  pattern that must not be built. It is not a transport.
- **Weakening `ProcessManager`** - refused; the security chain stays intact.

## 10. The exact missing piece

A **Termux-side component that exposes a bidirectional stream**. Concretely, one
of:

- a companion APK bound by the Harness, receiving `ParcelFileDescriptor`
  pipes for stdin/stdout and relaying them to the gateway; or
- a Termux plugin app Termux can start, speaking a defined protocol over a
  socket the Harness may reach.

Neither exists in Termux 0.118.3. Until one is built and shipped, the Hermes
**runtime** transport cannot be implemented - only its **provisioning** can.

Termux:API was the closest existing relative, but it is not installed and its
result delivery is one-shot as well, so it does not close the gap either.

## 11. Remaining blockers

1. No streaming IPC exists (blocking the runtime transport).
2. `allow-external-apps` must be enabled **by the user** inside Termux. This
   cannot be done by the Harness and should never be automated.
3. The RUN_COMMAND execution probe did not complete on-device even after all
   three gates were satisfied - no `probe.txt` was produced and Termux logged
   nothing after the `RUN_COMMAND_PATH` error was resolved. Recorded as
   **UNKNOWN**, not as success.
4. `connectedAndroidTest` reinstalls the app, which resets runtime
   permissions; granting then running via Gradle silently re-loses the grant.
   Tests must install + grant + `am instrument` separately.

## 12. Implementation plan

1. `TermuxProvisioningChannel` - one-shot `RUN_COMMAND`, behind
   `allow-external-apps`; used only for install/setup, never for streaming.
2. `TermuxAvailability` - explicit `NOT_INSTALLED / PERMISSION_MISSING /
   EXTERNAL_APPS_DISABLED / AVAILABLE` states surfaced to the UI.
3. `TermuxRuntimeBackend` - **blocked** until a streaming bridge exists; it must
   not claim readiness in the meantime.
4. Revisit if upstream Termux adds a streaming service, or if a companion APK
   is accepted.

## Evidence classification

- **VERIFIED FROM SOURCE**: single callable service; `PendingIntent` result
  delivery; `EXTRA_STDIN` is a String; no socket/AIDL/pipe surface.
- **OBSERVED ON DEVICE**: permission denied into Termux sandbox; no
  `termux-service`; permission `prot=dangerous`; `allow-external-apps` refusal;
  mandatory `RUN_COMMAND_PATH`; Shizuku installed but not running; FGS
  notification channel raised.
- **INFERRED**: that a companion APK would close the gap (standard Android
  capability, but not built or tested here).
- **UNKNOWN**: why the execution probe produced no file once all gates passed.
- **BLOCKED**: the bidirectional runtime transport itself.
