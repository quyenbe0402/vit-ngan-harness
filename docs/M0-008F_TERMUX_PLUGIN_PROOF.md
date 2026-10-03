# M0-008F Termux Plugin Proof

## Headline

The plugin **built and installed successfully**, and its signer **matched the
installed Termux exactly**. But it was **not admitted into the Termux shared
UID**, which invalidates the central assumption of M0-008E.

## 1. Termux version

Termux 0.118.3 (GitHub release build), `sharedUser=.../10361`.

## 2. Plugin architecture

A minimal Termux-shared-UID plugin declaring
`android:sharedUserId="com.termux"` and one exported service guarded by a custom
`dangerous` permission. Binder methods are fixed and structured:
`createTestSession()`, `closeTestSession(id)`, `sessionState(id)`. There is no
method that accepts a command string or a path.

## 3. Signer proof — PASSED

| Artefact | SHA-256 |
|---|---|
| Plugin `app-release.apk` | `b6da0148...8ee5e1` |
| Installed Termux 0.118.3 | `b6da0148...8ee5e1` |

Both `CN=APK Signer, OU=Earth, O=Earth`. Identical.

## 4. Installation proof — PASSED

```
adb install -r app-release.apk  ->  Success
```

## 5. Shared UID proof — **FAILED**

```
package:dev.vitngan.termuxbridge uid:10384
package:com.termux              uid:10361
```

The plugin received its **own** UID, 10384, and did **not** join `com.termux`
(10361) despite declaring the sharedUserId **and** presenting a matching
signing certificate.

**OBSERVED ON DEVICE.** No logcat warning explained it; the package manager
simply allocated a fresh appId.

Consequence: the plugin does **not** run in the Termux sandbox, and therefore
**cannot exec Hermes**. The `sharedUserId` route is not available on this
Android 16 device for a third-party package.

## 6. Binder contract

As designed: control over Binder, bulk bytes over three unidirectional
`ParcelFileDescriptor` pipes (stdin, stdout, stderr). `createPipe()` is never
described as duplex.

## 7. Caller authorization

Shared-UID membership is explicitly **not** treated as identity, because the
Termux signing key is public. The service resolves `Binder.getCallingUid()` to
its packages via `PackageManager.getPackagesForUid` and pins
`dev.vitngan.harness`, rejecting anything else **before** spawning a child.

## 8. What was not proven

Because the plugin never obtained Termux's UID, the end-to-end proof could not
run:

- A. Harness -> child stdin — **not proven**
- B. child stdout -> Harness — **not proven**
- C. child stderr -> Harness — **not proven**
- D. child exit -> Harness — **not proven**
- E. graceful shutdown — **not proven**
- F. second session — **not proven**

Writing those tests against an unreachable service would have produced mock
results, so they were not written.

## 9. Result

**NO, BLOCKED** - specifically, Model A's shared-UID premise fails on this
device.

The signing half of Model A is sound: the key is public and the digests match.
The failure is that Android did not honour `android:sharedUserId` for this
third-party package. `sharedUserId` is deprecated, and on this Android 16 build
it does not admit a new package into an existing shared UID even with a
matching signer.

## 10. What this invalidates

M0-008E concluded "Model A is deployable". That conclusion depended on
shared-UID membership and is **not** supported by this experiment. The
correction is recorded rather than left standing.

## 11. What would still be required

A component that genuinely executes in Termux' UID. That means either:

- a build of Termux itself carrying the bridge (same applicationId, same
  process, so no shared UID is needed), or
- upstream Termux exposing an interface that can host the bridge.

Both are Model B / Model D. Neither is available to this project today.

## Classification

- **VERIFIED FROM SOURCE**: plugin manifest declares sharedUserId; Termux
  declares the same; both signers are `CN=APK Signer, OU=Earth, O=Earth`.
- **OBSERVED ON DEVICE**: plugin installed successfully; plugin UID is 10384;
  Termux UID is 10361; they are not equal; the plugin therefore is not in the
  Termux sandbox.
- **INFERRED**: that `sharedUserId` is being ignored on this Android 16 build.
- **UNKNOWN**: whether a different manifest formulation could be admitted.
- **BLOCKED**: the whole FD-stream proof, and therefore TermuxRuntimeBackend.
