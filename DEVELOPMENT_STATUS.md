# Development Status

> This file is the **shared handoff state** of the project.
> Both agents may update it. **Every update must be committed.**
> A claim in this file is a claim about the real, measured state of the
> project. If a line says PASS, something was actually run and it passed.

**Last updated:** 2026-10-02
**Updated by:** Cline Desktop (development environment setup)

---

## Current milestone

**M0 — Environment and architecture preparation.**

M0 is complete when the development environment is usable, the workflow is
documented, and the Hermes Android project has been audited.

| Milestone | Scope | State |
|-----------|-------|-------|
| M0 | Development environment + architecture audit | IN PROGRESS |
| M1 | (not yet defined — depends on the M0 audit) | NOT STARTED |

## Current task

| Field | Value |
|-------|-------|
| Task ID | `M0-ENV-001` (environment setup) then `M0-001-dryrun` (workflow dry run) |
| Title | Prepare development environment and agent workflow |
| Assigned to | Cline Desktop |
| State | COMPLETE |

Both were completed. The dry run found and repaired five real script
defects that reading the code had not. See
`handoff/reports/M0-001-dryrun-integration.md`.

The next task is **`M0-001` — architecture audit of the Hermes Android
project.** It has not been started, and per the stop condition of this task
it is not started automatically.

## Repository branch

| Field | Value |
|-------|-------|
| Branches | `main`, `develop` (identical), plus the Cline repair branch |
| Integration branch | `develop` |
| Remote `origin` | **NOT CONFIGURED** |

## Commit

| Field | Value |
|-------|-------|
| HEAD | *populated at commit time* |
| Working tree | clean at handoff |

## Agent status

| Agent | Status | Notes |
|-------|--------|-------|
| Claude | READY | Has never received a task in this workflow yet |
| Cline | READY | Environment prepared; awaiting a Claude handoff |

## Build status

| Field | Value |
|-------|-------|
| Build | **NOT CONFIGURED** |
| Reason | The Android product project does not exist yet. This repository contains development infrastructure only. |
| Command | `.\scripts\build.ps1` — reports `NOT CONFIGURED` (exit 3) rather than failing |
| Toolchain | Gradle wrapper not present; standalone Gradle not installed (not required) |

## Unit test status

| Field | Value |
|-------|-------|
| Unit tests | **NOT CONFIGURED** |
| Reason | No test source exists yet |
| Command | `.\scripts\test.ps1` — reports `NOT CONFIGURED` |

## Integration test status

| Field | Value |
|-------|-------|
| Integration tests | **NOT CONFIGURED** |
| Reason | No instrumentation test source exists yet |

## Physical device status

| Field | Value |
|-------|-------|
| State | **CONNECTED** |
| Serial | `b36d068a` |
| Device | Xiaomi Redmi `24069RA21C` |
| Android version | 16 |
| SDK | 36 |
| CPU ABI | `arm64-v8a` |
| ADB state | `device` (authorised) |
| ADB | WORKING — adb 1.0.41 (Version 37.0.1-15733141), server on 5037 |
| Device validation performed | **NONE** — there is no application to install yet |
| Primary test environment | Physical phone over USB-C (not an emulator) |

Device **connectivity** is confirmed. Device **validation** has never been
run, because the Android project does not exist. The two are different
claims and are recorded separately.

Note for future tasks: SDK 36 is a recent platform with stricter
background and permission behaviour, and `arm64-v8a` is the only ABI — a
native library built for another ABI will fail here but not on an x86_64
emulator.

See `docs/ANDROID_DEVICE_WORKFLOW.md`.

## Known issues

1. **No Git remote.** `git remote -v` returns nothing. Fetch and push are
   impossible until the owner creates a GitHub repository and runs
   `git remote add origin <url>`.
2. **No Git commit identity.** `user.name` / `user.email` are unset, so
   commits fail until they are configured. *(Resolved during the dry run — see
   Blockers.)*
3. **No GitHub CLI.** `gh` is not installed. Pull requests must be created in
   the web UI, or `gh` must be installed.
4. **No Android project.** The build and test scripts report
   `NOT CONFIGURED` by design until it exists.
5. **Credential helper depends on an out-of-band environment variable.** The
   per-user Git `credential.helper` is overridden, so authentication may
   fail in shells where that variable is absent. Documented in
   `docs/GITHUB_AUTH_SETUP.md` §4.
6. **`gh auth status` was not run** because `gh` is not installed. The
   prerequisite is documented instead.
7. **No push has ever been performed.** The dry run confirmed the full local
   loop, but the push stage is BLOCKED, so no commit has left this machine.

## Blocked issues

| ID | Blocker | Blocks | Owner action |
|----|---------|--------|--------------|
| B1 | No `origin` remote | The entire push/handoff chain | Create the GitHub repo; `git remote add origin <url>` |
| B4 | No GitHub authentication verified | Push | Complete `docs/GITHUB_AUTH_SETUP.md` |
| B3 | *(RESOLVED)* No physical device | Device validation | Device `b36d068a` is now connected and authorised |
| B2 | *(RESOLVED)* No commit identity | Any commit | Identity configured for this repository |

**B1 and B4 are the remaining hard blockers.** They prevent the
Claude → GitHub → Cline handoff from completing. Everything up to the push
has been verified working locally.

## Dry run

| Field | Value |
|-------|-------|
| Task | `workflow-test.txt` containing `Hello` — harmless, not product code |
| Claude stages | PASS — branch created, file written, committed |
| Cline stages | PASS — inspected, device connected, Logcat captured, 5 defects found and repaired, re-validated |
| Repairs committed | `69554d0` on `cline/M0-001-fix-scripts` |
| Cleanup | PASS — test branches and `workflow-test.txt` removed; nothing left behind |
| **PUSH TEST** | **BLOCKED** — no `origin` remote and no verified credentials |

**PUSH TEST: BLOCKED.** Not faked, and not worked around. Every other stage
of the loop executed for real.

The dry run earned its keep: it found five genuine defects, including one
where the Logcat summary reported 0 errors on a capture that contained
3,178. Reading the code had not revealed any of them.

## Next task

| Field | Value |
|-------|-------|
| Task ID | `M0-001` |
| Title | Architecture audit of the Hermes Android project |
| Owner | Claude Web (architecture) then Cline Desktop (validation) |
| State | NOT STARTED |

---

## Change log

| Date | Agent | Change |
|------|-------|--------|
| 2026-10-02 | Cline | Initial status file. Development environment prepared and documented. |
| 2026-10-02 | Cline | Workflow dry run executed. 5 script defects found and repaired (`69554d0`). Physical device detected and its properties recorded. Push stage reported BLOCKED. |
