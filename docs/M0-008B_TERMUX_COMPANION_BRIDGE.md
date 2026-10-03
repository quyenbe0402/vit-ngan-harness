# M0-008B Termux Companion Bridge

## 1. Problem

M0-008A established that Termux 0.118.3 offers no bidirectional IPC. This
milestone asks whether a **Termux-side companion** can create one, and builds
the smallest real prototype that would settle it.

## 2. Evidence inherited from M0-008A

- The Harness cannot exec into the Termux sandbox (UID boundary, observed).
- `RUN_COMMAND` is request/response only; `EXTRA_STDIN` is a one-shot String.
- No Socket / AIDL / Messenger surface exists in Termux 0.118.3.

## 3. Candidate architectures

| # | Architecture | Verdict |
|---|---|---|
| A | A separate companion APK that execs the gateway | **Impossible**: same UID wall. Only Termux's UID may run Hermes. |
| B | A helper APK bound over IPC | Same wall, plus nothing to bind to. Rejected. |
| C | **Termux-side script, started via `RUN_COMMAND`, exposing a loopback TCP socket** | Built and tested. |

C is the only shape where the process keeps Termux's UID (so it *can* run
Hermes) while the Harness talks to it over a channel Android nominally permits.

## 4. Source references

- `termux/termux-app` @ `v0.118.3`, `RunCommandService.java` — one-shot result
  delivery via `EXTRA_PENDING_INTENT`; `EXTRA_STDIN` is a String.
- `tui_gateway/entry.py` @ `eaecc99c` — Hermes exits on stdin EOF and writes
  newline-delimited JSON to stdout; that is the stream shape to carry.
- Prototype scripts archived under `docs/prototype/`.

## 5. Selected architecture

```
Harness  ──TCP 127.0.0.1──▶  hbridge.py  ──pipes──▶  child (later: Hermes)
            (app UID)          (Termux UID)
```

Two loopback ports: one transparent stdin/stdout byte pipe, one control channel
carrying stderr and exit status as JSON lines. The companion is a **transport**,
not a policy authority.

## 6. IPC primitive

Loopback TCP (`SOCK_STREAM`), bound to `127.0.0.1` only. A token is required on
the first line of each connection.

## 7. Permission model

- `com.termux.permission.RUN_COMMAND` (dangerous) — declared and granted.
- Termux-side `allow-external-apps = true` — enabled for the experiment.
- `android.permission.INTERNET` — tried, then **reverted** (see §12).

## 8. Data flow — what actually worked

- **VERIFIED**: Termux can bind a loopback listener; observed in `/proc/net/tcp`
  as `0100007F:9985` state `0A` (LISTEN), uid 10361.
- **VERIFIED**: the companion accepted a cross-UID connection from adb shell
  (uid 2000) and returned the echoed payload.
- **BLOCKED**: the Harness app (uid 10372) **cannot** reach that listener.

## 9. Physical-device experiment

```
java.net.SocketTimeoutException: failed to connect to /127.0.0.1
  (port 39301) from /127.0.0.1 (port 41746) after 8000ms
```

The listener was present and in LISTEN state, and no SELinux `avc denied` was
logged for the app. The app bound its own loopback source port and sent a SYN
that was silently dropped. Repeating the connect from uid 2000 succeeded; from
the app UID it times out.

This is **per-UID loopback isolation** on this device (Xiaomi HyperOS, Android 16):
a property of the platform configuration, not of the companion design.

## 10. Lifecycle

- **VERIFIED**: the Termux-side process survived many connections; the companion
  loop-accepted successive sessions and spawned a child per session.
- **VERIFIED**: on client close the companion terminated the child and accepted a
  new session, i.e. reconnect works *within* Termux.
- **UNKNOWN**: app backgrounding/foreground, companion death detection from the
  app side, reclaim behaviour — unreachable, because the app never connected.

## 11. Security model

Unchanged: Android policy → authorised request → IPC → Termux companion →
supervised child. The companion runs a fixed command from trusted config, never
an LLM string and never a UI string. No root, no Shizuku, no
Accessibility/MediaProjection/DeviceOwner. Termux's UID boundary is preserved
because the gateway keeps running inside Termux.

## 12. The INTERNET permission experiment

The first connect failed with `EPERM`: Android refuses *any* socket to an app
without `INTERNET`, loopback included. The permission was declared, the app
rebuilt and reinstalled, and `granted=true` confirmed.

The failure then changed from `EPERM` to `SocketTimeoutException` — which proves
the permission did its job and that a **second, deeper** boundary exists beneath
it. Because it did not make the transport work, it was **reverted**: shipping a
permission that buys nothing is not justified. The manifest declares only
`com.termux.permission.RUN_COMMAND`.

## 13. Hermes compatibility assessment

Not assessed beyond the shape. Hermes needs exactly one bidirectional,
newline-delimited JSON byte pipe that stays open. The companion relays a child
process's stdin/stdout transparently and carried a 200 kB frame in the uid-2000
check, so the *relay* is adequate. Whether an app UID may open it is the blocking
question, and on this device it may not.

## 14. Remaining blockers

1. **Per-UID loopback isolation** blocks every loopback-based companion here.
   This is the concrete missing platform capability.
2. Any working alternative must avoid loopback: a bound `Service` with
   `ParcelFileDescriptor` pipes needs a shared UID, which Android does not permit
   between independent apps; Shizuku is rejected on privilege grounds.
3. So no Termux-side streaming path exists for this app on this device via a
   mechanism Android currently exposes.

## 15. Classification

- **VERIFIED**: Termux loopback listener reachable from uid 2000 but not from the
  app uid; INTERNET necessary but insufficient; `RUN_COMMAND` is one-shot.
- **OBSERVED**: the `/proc/net/tcp` LISTEN entry; the exact timeout exception;
  the absence of any `avc denied` for the app.
- **INFERRED**: that an OEM loopback-isolating policy (HyperOS) is the cause.
- **UNKNOWN**: whether a non-loopback IPC (bound service, ContentProvider pipe,
  `MemoryFile` handover) could bridge the two UIDs.
- **BLOCKED**: the bidirectional Android↔Termux stream itself.
