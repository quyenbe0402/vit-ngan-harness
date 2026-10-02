# Android Physical Device Workflow

The primary Android test environment is a **real physical phone connected by
USB-C**. Not an emulator.

Emulators are present on this machine and are fine for a quick sanity check,
but they do not reproduce the failures that actually matter: OEM background
restrictions, real permission flows, real memory pressure, manufacturer
specific behaviour. An emulator PASS is not a device PASS.

---

## Current state: DEVICE CONNECTED

A physical device is connected and authorised. Measured directly via ADB:

```
> adb devices -l
List of devices attached
b36d068a    device product:peridot model:24069RA21C device:peridot transport_id:1
```

| Property | Value |
|----------|-------|
| Serial | `b36d068a` |
| `ro.product.manufacturer` | Xiaomi |
| `ro.product.brand` | Redmi |
| `ro.product.model` | `24069RA21C` |
| `ro.build.version.release` | 16 (Android 16) |
| `ro.build.version.sdk` | 36 |
| `ro.product.cpu.abi` | `arm64-v8a` |
| ADB state | `device` (authorised) |

ADB itself is working — adb 1.0.41 (Version 37.0.1-15733141), server on
port 5037.

**SDK 36 / Android 16 is a recent platform.** This is significant for
validation: runtime permission behaviour, background-start restrictions, and
foreground-service requirements are all stricter than on the older targets
many Android samples assume. Any permission-dependent feature must be
tested specifically against this behaviour, and the app's `targetSdk` must
be considered when judging whether a failure is a bug or expected platform
behaviour.

**`arm64-v8a` only.** The APK must contain a 64-bit ARM native library for
any native dependency. A `armeabi-v7a`-only or missing native library will
fail to install or fail at load time on this device — and would not fail on
an x86_64 emulator, which is exactly the kind of difference a real device
exists to expose.

> Earlier in the same setup session `adb devices` reported no devices, so the
> baseline documents record `NOT_CONNECTED`. The device was connected
> afterwards and this section was updated with the real measurements. Both
> states were real at the time they were recorded.

---

## The chain

```
USB-C
  -> USB debugging enabled
  -> ADB authorised
  -> build
  -> install
  -> launch
  -> Logcat
  -> test
  -> repair
```

Every link must be verified. A failure at any link invalidates everything
after it.

---

## 1. USB-C connection

- Connect a **data-capable** USB-C cable. Many cables are charge-only, which
  produces a device that never appears in `adb devices` and looks like a
  software problem.
- If the phone prompts for a mode, choose **File Transfer / MTP**, not
  "Charge only".

## 2. Enable USB debugging

On the phone:

```
Settings -> About phone -> tap "Build number" 7 times
Settings -> Developer options -> USB debugging -> ON
```

On Android 8+, "USB debugging" is a separate toggle from the older
"USB debugging (Security settings)". Both may be needed.

## 3. Authorise ADB

The first connection shows an RSA fingerprint prompt on the phone. Tap
**Allow**. This is per-computer, and it is revoked if the developer options
are reset.

Verify:

```powershell
adb devices
```

Expected — the state column must read `device`:

```
List of devices attached
ABC123XYZ    device
```

| State | Meaning | Action |
|-------|---------|--------|
| `device` | Ready | Proceed |
| `unauthorized` | RSA prompt not accepted | Accept it on the phone |

`scripts/doctor.ps1` reports device presence; the property collection is
run by hand or by the integration report.

## 5. Build

```powershell
.\scripts\build.ps1
```

Produces the APK under `app/build/outputs/apk/`.

## 6. Install

```powershell
.\scripts\install-device.ps1 -PackageName <app-id> -UninstallFirst
```

`-PackageName` is a parameter. It is not hardcoded, because the Android
project and its application ID do not exist yet.

The script verifies the package is present on the device after install, so
a silently failed install cannot be mistaken for success.

Common install failures:

| Error | Cause | Fix |
|-------|-------|-----|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Existing build signed with a different key | `-UninstallFirst` |
| `INSTALL_FAILED_INSUFFICIENT_STORAGE` | Device full | Free space |
| `INSTALL_FAILED_VERIFICATION_FAILURE` | Install source blocked | Settings -> Install unknown apps |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | Newer build already installed | Uninstall first |

## 7. Launch

```powershell
.\scripts\launch-device.ps1 -PackageName <app-id> -ClearLogcat -ForceStop
```

`-ClearLogcat` before launch is deliberate: the capture then contains only
this run, so a startup crash cannot be lost in earlier noise.

The script resolves the launcher activity automatically and fails clearly
if it cannot, rather than silently doing nothing.

## 8. Logcat

```powershell
.\scripts\collect-logcat.ps1 -DurationSeconds 20
```

Saves to `handoff/reports/logcat-<serial>-<timestamp>.txt` and prints a
summary: total lines, error count, and any `FATAL EXCEPTION`, process-death
or ANR lines, which are highlighted.

For a slow or intermittent failure, capture live and interact with the app
while it records:

```powershell
.\scripts\collect-logcat.ps1 -Live
```

Targeted capture is faster to read:

```powershell
.\scripts\collect-logcat.ps1 -Filter 'AndroidRuntime:E Hermes:V'
.\scripts\collect-logcat.ps1 -ShowCrashesOnly
```

## 9. Test

Scripts install and launch. They do not **use** the app.

A human must perform the actual task on the real device and observe:
permission prompts (including the denial path), activity lifecycle,
background/foreground transitions, and anything the build and unit tests
cannot reach.

## 10. Repair

If it fails:

1. Read the Logcat stack trace and find the cause
2. Fix it within scope, on a `cline/<task-id>-<short-name>` branch
3. Add a test that would have caught it
4. Re-run build, tests, and the device loop
5. Report the before/after result

A repair is not complete until it has been re-validated on the device.

---

## All-in-one

```powershell
.\scripts\full-device-test.ps1 -PackageName <app-id>
```

Runs build -> tests -> install -> launch -> Logcat -> summary, and writes
the summary to `handoff/reports/device-test-<timestamp>.txt`.

**Important:** a PASS from this script means the *pipeline* worked. It does
not mean the app behaves correctly. A human still has to use the app. The
script says so in its own output, deliberately.

---

## Permissions that will need real-device testing

When the Android project exists, these cannot be verified without a physical
phone, because each is a user-facing system decision:

- Runtime permission grant **and denial** (and the settings path when
  permanently denied)
- Notification permission
- Accessibility service enablement
- MediaProjection screen capture consent
- Battery-optimisation exemption prompts
- Background start restrictions

An emulator is particularly unreliable for these: it grants permissions
differently and has no battery optimisation.

---

## Troubleshooting

| Symptom | Likely cause | Action |
|---------|--------------|--------|
| Device absent from `adb devices` | Charge-only cable, bad port, or debugging off | Change cable/port, re-check developer options |
| `unauthorized` | RSA prompt dismissed | Reconnect and tap Allow |
| `offline` | adb server stale | `adb kill-server`, reconnect |
| `more than one device` | Emulator and phone both up | Use `-Serial <serial>`, or close the emulator |
| Install fails with signature error | Different signing key | `-UninstallFirst` |
| App launches then dies | Crash at startup | Clear Logcat, relaunch, capture immediately |
| Logcat empty | Buffer cleared after the fact | Always clear *before* launching |

| `offline` | Cable/adb server glitch | `adb kill-server`, reconnect |
| *(absent)* | Not detected | Cable, port, driver, or USB debugging off |
| `recovery` / `sideload` | Wrong mode | Unplug and reconnect normally |

## 4. Collect device properties

Only meaningful once a device is authorised:

```powershell
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.cpu.abi
```

Record these in `DEVELOPMENT_STATUS.md` and in the integration report. They
determine which behaviour is expected — permission models differ by SDK
level, and the ABI determines which native libraries the APK must contain.
