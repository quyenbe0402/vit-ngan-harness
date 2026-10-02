# Development Environment Baseline

**Captured:** 2026-10-02
**Captured by:** Cline Desktop (development environment setup agent)
**Machine:** Windows 10.0.26200 (build 26100), PowerShell 5.1 Desktop edition

This document is a **snapshot**. It records what was true when the
development environment was prepared. It is not modified automatically —
refresh it with `.\scripts\doctor.ps1` and update by hand when tooling changes.

> No product source was modified to satisfy a missing prerequisite. Every
> FAIL/WARNING below is a documented prerequisite for the human owner to
> resolve, not something this task worked around.

---

## 1. Summary table

| Component | State | Version / Value |
|-----------|-------|-----------------|
| Git | **PRESENT** | 2.55.0.windows.3 |
| Git repository | **PRESENT** (created by this task) | init'd at project root, branch `main` |
| Git remote `origin` | **NOT CONFIGURED** | — |
| Git commit identity | **NOT CONFIGURED** | `user.name` / `user.email` unset |
| GitHub CLI (`gh`) | **NOT INSTALLED** | — |
| SSH client | **PRESENT** | OpenSSH_for_Windows_9.5p2, LibreSSL 3.8.2 |
| PowerShell | **PRESENT** | 5.1.26100.9444 (Desktop) |
| Java (on PATH) | **NOT ON PATH** | — |
| Java (JDK, via `JAVA_HOME`) | **PRESENT** | OpenJDK 25.0.2 (Android Studio bundled JBR) |
| Gradle (standalone) | **NOT INSTALLED** | not required — project ships `gradlew` |
| Gradle wrapper | **NOT YET** | will arrive with the Android project |
| Node.js | **PRESENT** | v24.18.0 |
| npm | **PRESENT** | 11.16.0 |
| Python | **PRESENT** | 3.13.15 |
| ADB | **PRESENT** | 1.0.41 / Version 37.0.1-15733141 |
| Android SDK | **PRESENT** | `%LOCALAPPDATA%\Android\Sdk` |
| Android platforms | **PRESENT** | android-36, android-36.1, android-37.0, latest |
| Android build-tools | **PRESENT** | 36.0.0 |
| Android Studio | **PRESENT** | 261.26222.65.0-AI |
| Physical Android device | **CONNECTED** | Xiaomi Redmi 24069RA21C, Android 16 (SDK 36), arm64-v8a, serial `b36d068a` |
| Emulator images | **PRESENT** | available, but **not** the primary test target |

---

## 2. Repository baseline

Captured immediately after `git init`, before the first commit.

```
Repository root   : C:\Users\ADMIN\Downloads\dự án agent
Git version       : git version 2.55.0.windows.3
Current branch    : main (initialised, unborn)
Current commit    : (none — repository had no commits at capture time)
Remote origin     : (none)
Remotes           : (none)
Available branches: main (unborn)
Working tree      : untracked files present (pre-existing loose files)
```

The workspace previously contained **no Git repository at all**. It was a
plain directory of loose files. A repository was initialised as part of this
environment setup, because every script and protocol in this workflow assumes
a Git repository exists.

## 3. Git configuration state

Read from `git config --list --show-origin` (safe, no secrets printed):

```
C:/Program Files/Git/etc/gitconfig   credential.helper=manager
C:/Program Files/Git/etc/gitconfig   init.defaultbranch=master
C:/Users/ADMIN/.gitconfig            credential.helper=<cleared>
C:/Users/ADMIN/.gitconfig            filter.lfs.* (git-lfs installed)

## 4. Environment variables

| Variable | Value on this machine | Note |
|----------|-----------------------|------|
| `ANDROID_HOME` | `C:\Users\ADMIN\AppData\Local\Android\Sdk` | set |
| `JAVA_HOME` | `C:\Program Files\Android\Android Studio\jbr` | set, but `java` is **not on PATH** |

`JAVA_HOME` points at the JBR bundled with Android Studio (OpenJDK 25). This
is sufficient for Android builds. Because `java` is not on `PATH`, bare
`java -version` in a fresh terminal fails. Scripts therefore probe `PATH`
first and then fall back to `%JAVA_HOME%\bin\java.exe`.

> **Compatibility note for the project owner:** the Android Gradle Plugin
> version that ships with the future project will declare its own supported
> JDK range. If the build later rejects JDK 25, install a JDK 17 LTS and
> point `JAVA_HOME` at it. That is a project decision, not a setup task, so
> it was left alone.

## 5. Android SDK inventory

```
ANDROID_HOME: C:\Users\ADMIN\AppData\Local\Android\Sdk

platform-tools/        -> adb 1.0.41 (Version 37.0.1-15733141)
platforms/             -> android-36, android-36.1, android-37.0, latest
build-tools/           -> 36.0.0
cmdline-tools/         -> latest
emulator/              -> present
system-images/         -> present
licenses/              -> present (SDK licences accepted)
```

Platform-tools, a platform, and build-tools are all present, so the machine
is ready to build once the Android project exists.

## 6. Android Studio

```
Path    : C:\Program Files\Android\Android Studio
Version : 261.26222.65.0-AI
Binary  : bin\studio64.exe  (detected)
```

## 7. Physical Android device

Two observations were made during setup, at different times. Both are
recorded as they were.

**First observation (initial baseline capture):**

```
> adb devices
List of devices attached
        (no entries)
```

The ADB server started successfully on port 5037, so ADB was working. No
device was attached, so the device property collection was **not run** — no
manufacturer, model, Android version, SDK level, or ABI was obtained. No
values are recorded for that observation, because none were measured.

**Second observation (later in the same session):** a device was connected
and authorised. Properties collected from it:

| Property | Value |
|----------|-------|
| Serial | `b36d068a` |
| Manufacturer | Xiaomi |
| Brand | Redmi |
| Model | `24069RA21C` |
| Android release | 16 |
| SDK | 36 |
| CPU ABI | `arm64-v8a` |

**Current state: DEVICE CONNECTED.** The live state is always whatever
`adb devices` says right now; run it rather than trusting this document.

To resolve a `NOT_CONNECTED` state: see
`docs/ANDROID_DEVICE_WORKFLOW.md` §1–§3.

## 8. Tooling not present

| Missing | Impact | Action for the owner |
|---------|--------|----------------------|
| GitHub CLI (`gh`) | `git-push-branch.ps1 -CreatePullRequest` cannot open a PR automatically; must use the web UI | Install: `winget install --id GitHub.cli` |
| Java on `PATH` | Bare `java`/`javac` unavailable outside Android Studio | Optional: add `%JAVA_HOME%\bin` to PATH |
| Standalone Gradle | None — the project will ship `gradlew` | No action needed |
| Git `user.name` / `user.email` | **Commits fail** | Set identity (see `docs/GITHUB_AUTH_SETUP.md`) |
| Git remote `origin` | **Fetch/push impossible** | Create the GitHub repo, then `git remote add origin <url>` |

## 9. Baseline verification commands

Run these at any time to re-verify this baseline:

```powershell
.\scripts\doctor.ps1          # human-readable PASS/FAIL/WARNING report
.\scripts\doctor.ps1 -Json    # machine-readable
.\scripts\git-status.ps1      # repository state
adb devices                   # device presence
```

## 10. Baseline verdict

The machine is **sufficiently equipped** to build, test and deploy an Android
project once the project source exists, and a physical device is connected
and ready to receive an APK.

The remaining genuine blockers are identity and remote — both of which
require the human owner — and they are documented in
`docs/GITHUB_AUTH_SETUP.md`. Device connectivity is no longer a blocker.

```

Observations and recommended actions:

1. **`init.defaultbranch=master`** (system config) — this task explicitly
   created the repository with `-b main` so the project uses `main`, not
   `master`. If a future repository is created without `-b`, it will default
   to `master` and break the documented branch model.
2. **No `user.name` / `user.email`** — commits will fail until this is set.
   See `docs/GITHUB_AUTH_SETUP.md`.
3. **`credential.helper=manager`** (Git Credential Manager) is present at the
   system level, which is the recommended HTTPS auth path on Windows.
4. **A per-user `credential.helper` override is present** that injects a
   token from a shell environment variable. It clears the system helper and
   replaces it with a custom function. This is a **security concern**: it
   disables Git Credential Manager for this account, and the token's lifetime
   and scope are outside the repository. It is *not* stored in the repository,
   so it is not a leak in Git history — but the workflow depends on it and
   nothing in this repository guarantees it is present. See
   `docs/GITHUB_AUTH_SETUP.md` and `docs/SECRETS_POLICY.md`.
