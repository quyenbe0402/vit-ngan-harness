# M0-008C Cross-UID Binder / ParcelFileDescriptor Audit

## 1. The M0-008B assumption under review

M0-008B asserted: *"Bound Service + `ParcelFileDescriptor` pipes require the same
UID."* That was stated as an Android platform rule. **It was wrong**, and this
milestone re-verifies it rather than defending it.

## 2. Android platform capability — **A = YES**

**VERIFIED ON DEVICE.** The Harness (uid 10379) obtained a valid
`ParcelFileDescriptor` from Termux' provider (uid 10361) across the UID
boundary:

```
CROSSUID_PFD_PROBE=FD_ARRIVED valid=true
CALLER_UID=10379
```

Cross-UID communication through an exported component + Binder +
ParcelFileDescriptor transfer **works on this device**, gated by permission and
by Termux' own `allow-external-apps` policy. Android supports it officially; the
experiment confirms it is not merely theoretical here.

## 3. Termux 0.118.3 capability — **B = NO (for streaming)**

Manifest read from the **installed APK itself** (aapt2 dump):

| Component | exported | bindable | streams? |
|---|---|---|---|
| `com.termux.app.TermuxService` | **false** | `onBind` → `mBinder` | 0 `ParcelFileDescriptor`, no AIDL |
| `com.termux.app.RunCommandService` | true (RUN_COMMAND) | start only | one-shot result |
| `TermuxOpenReceiver$ContentProvider` | true (RUN_COMMAND) | `openFile` | **regular files only** |
| `TermuxDocumentsProvider` | true | SAF | files only |

Source findings:
- `TermuxService.onBind()` returns a real `IBinder`, but `exported=false` means
  Android refuses a bind from another app, and the class contains **zero**
  `ParcelFileDescriptor` references and no AIDL.
- `TermuxOpenReceiver$ContentProvider.openFile()` does `new File(uri.getPath())`
  and returns a descriptor for that **file**. Counts in that file:
  `openPipeHelper` **0**, `createPipe` **0**, `createSocketPair` **0**. It is a
  file-open provider, not a streaming provider.

## 4. Binder analysis

`exported` + `permission` is the whole gate, enforced by the framework —
observed in logcat as it resolved our caller:
`caller is ProcessRecord{...:dev.vitngan.harness/u0a379}`.

Termux ships **no AIDL interface** (source scan) and no `Messenger` endpoint.

## 5. ParcelFileDescriptor analysis

Transfer works (proved in §2). The gap is not the primitive — it is that Termux
never calls `openPipeHelper`, `createReliablePipe` or `createSocketPair`, so
there is no descriptor representing a *live process channel* to hand back.

## 6. ContentProvider analysis

`ContentProvider.openFile()` **may** return a pipe or socket pair, and
`openPipeHelper()` exists for exactly that. Termux' provider implements neither;
it returns a descriptor opened on a regular file. The platform hook is present
and unused.

## 7. SharedMemory analysis

`SharedMemory` / `MemoryFile` were considered and **rejected** on their own terms:
they are shared byte regions, not streams. They give no blocking read, no natural
backpressure and no process-exit signal. Using them as a transport would mean
re-implementing pipes badly. Recorded as unsuitable; not pursued.

## 8. Physical-device tests

5 instrumented tests (`CrossUidBinderPfdTest`), all passing. The first probe
returned `REFUSED FileNotFoundException` because the URI was malformed and the
framework never reached Termux (`No provider info for content provider
com.termux.files/...`). That was a bug in my test, not a platform finding, and
it is recorded rather than quietly fixed.

## 9. Security model

Unchanged. The Harness keeps policy authority; the Termux-side bridge is a
transport; the specific process is fixed by trusted config. Cross-UID IPC would
be protected by explicit component identity, the existing `RUN_COMMAND`
permission, explicit intent, caller-UID validation by the framework, and
per-connection token binding. No root, no Shizuku, no unrestricted exported
component, no loopback workaround, no filesystem polling.

## 10. Process ownership model

Unchanged and explicit: an Android-owned process goes through `ProcessManager`;
a Termux-owned process is represented as a **remote session** reached over IPC.
`ProcessManager` was not modified.

## 11. Result

**B. CROSS-UID IPC EXISTS BUT TERMUX NEEDS A NEW BRIDGE COMPONENT**

Deliberately **not** collapsed into "blocked". The platform supports exactly the
primitive required, proven on this device. What is missing is a Termux-side
component that uses it for streaming.

## 12. The exact missing component

A Termux-side exported service (or an extension of the existing provider) that:
accepts an authenticated `openFile`-style request; calls `openPipeHelper` or
`ParcelFileDescriptor.createPipe()` / `createSocketPair()` to return a **live
duplex descriptor**; and binds that descriptor to a Termux-owned child process's
stdin/stdout. None of this exists upstream. It is new code, not configuration.

## 13. Next implementation step

Decide between: (a) contribute such a bridge upstream to Termux, (b) ship a
Termux plugin/companion Termux can launch, or (c) accept Shizuku. Only then can
`TermuxRuntimeBackend` be written against a transport known to work.

## Classification

- **VERIFIED FROM SOURCE**: `TermuxService` exported=false, 0 PFD, no AIDL;
  `TermuxOpenReceiver$ContentProvider` openFile returns regular files with 0
  openPipeHelper/createPipe/createSocketPair; exported flags from the APK.
- **OBSERVED ON DEVICE**: `FD_ARRIVED valid=true` across uid 10379 to uid 10361;
  framework caller-identity resolution; the malformed-URI failure before it.
- **INFERRED**: that adding openPipeHelper to a Termux-side provider would close
  the gap - standard Android, not built or tested here.
- **UNKNOWN**: whether upstream Termux would accept such a contribution.
- **BLOCKED**: nothing at the platform level; the blocker is a missing
  Termux-side component.