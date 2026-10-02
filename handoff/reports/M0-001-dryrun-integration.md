# Task
Task ID: M0-001-dryrun
Title:  Workflow dry run (harmless test task, not product work)

# Agent
Cline Desktop
Date: 2026-10-02

# Branch
Branch:       cline/M0-001-fix-scripts
Base branch:  develop
Remote:       origin (NOT CONFIGURED)

# Commit
Commit: 69554d0
       69554d0
Status: COMMITTED LOCALLY / PUSH BLOCKED

# Files Changed
- scripts/lib/Common.ps1          (modified)
- scripts/collect-logcat.ps1      (modified)
- scripts/full-device-test.ps1    (modified)
- scripts/git-push-branch.ps1     (modified)
Total: 4 files, +35 / -10 lines

# Build
Result: NOT CONFIGURED
Command: .\scripts\build.ps1  ->  exit 3
Notes: No `gradlew.bat` in the project root. The Android product project
       does not exist yet. The script reports this explicitly rather than
       failing, which is the intended behaviour. This is not a build failure.

# Tests
Result: NOT CONFIGURED
Command: .\scripts\test.ps1  ->  exit 3
Notes: No test source exists yet. No test was executed. Nothing is claimed
       about test correctness.

Additionally, all 15 PowerShell scripts were verified to parse cleanly using
the PowerShell AST parser. 12 were executed against real inputs.

# Physical Device
Result: NOT TESTED
Device: Xiaomi Redmi 24069RA21C, Android 16, SDK 36, arm64-v8a, serial b36d068a
Command: adb devices, collect-logcat.ps1
Notes: Device connectivity IS confirmed and ADB works. Device *validation*
       was not performed because there is no application to install. The two
       claims are separate and are recorded separately.
       A live Logcat capture of 72,605 lines was taken as a real test of
       `collect-logcat.ps1`, and it correctly surfaced 3,178 error lines.

# Repairs

Five defects, all found by executing the scripts rather than reading them.

1. **`scripts/lib/Common.ps1` — false FAIL on successful commands.**
   `Invoke-Git` let git's own output flow into the PowerShell success stream
   alongside the returned exit code. A caller doing `$code = Invoke-Git ...`
   received an array, so `$code -ne 0` was true even on full success. Branch
   creation printed `[FAIL]` while git had actually succeeded. Fixed by
   writing git output to the host and returning only the exit code.

2. **`scripts/git-push-branch.ps1` — parse error; script could not run.**
   `"...$(@($porcelain).Count...)"` is invalid in PowerShell 5.1
   ("$(subexpression) is missing the closing ')'"). Replaced with a
   pre-wrapped array.

3. **`scripts/collect-logcat.ps1` — error detection silently reported zero.**
   The summary matched `\sE\s` (letter between spaces), but `adb logcat -v
   time` emits `E/Tag`. A real capture containing 3,819 error lines reported
   "Error lines : 0". This is the most dangerous defect found: the tool whose
   job is to surface runtime errors hid all of them. Fixed and re-verified
   against the same device, now correctly reporting 3,178.

4. **`scripts/full-device-test.ps1` — parse error; script could not run.**
   `switch` used as an expression inside parentheses. Replaced with
   if/elseif.

5. **Array enumeration under StrictMode.** ADB device-line parsing relied on
   `.Count` on a non-array. With a single device this threw
   `PropertyNotFoundStrict`. Fixed in all four ADB scripts.

Not repaired, by design: nothing outside the task scope was touched, and no
architecture was changed.

# Known Issues

1. **Push is BLOCKED.** No `origin` remote and no verified credentials. The
   push stage of the loop has never executed successfully and cannot until
   the owner configures GitHub. Not worked around.
2. **Build and test are NOT CONFIGURED.** Expected — no Android project.
3. **No pull request could be created**, because `gh` is not installed and
   there is no remote. `git-push-branch.ps1 -CreatePullRequest` will report
   this and fall back to instructing the user.
4. **Two scripts were never executed:** `git-sync.ps1` (requires a remote
   with an upstream) and `test.ps1` (requires a Gradle project). They parse
   cleanly but have not been run against real inputs.

# Architecture Impact
NONE

No design change. Only tooling defects were fixed. No product code was
written or modified.

# Dry Run Result

| Stage | Result |
|-------|--------|
| Claude creates branch | PASS — `claude/M0-001-dryrun` from `develop` |
| Claude writes and commits | PASS — `workflow-test.txt` containing `Hello` |
| Claude pushes to GitHub | **BLOCKED** — no origin remote |
| Cline fetches | **BLOCKED** — depends on the above |
| Cline inspects branch and diff | PASS — `git-check-branch.ps1` |
| Cline builds | NOT CONFIGURED |
| Cline runs tests | NOT CONFIGURED |
| Cline connects device | PASS — b36d068a |
| Cline installs | FAIL as expected — no APK exists |
| Cline launches | FAIL as expected — package not installed |
| Cline collects Logcat | PASS — 72,605 lines, 3,178 errors detected |
| Cline diagnoses and repairs | PASS — 5 defects fixed |
| Cline re-validates | PASS — 0 FAIL, all scripts parse and run |
| Cline commits repair | PASS — 69554d0 |
| Cline pushes repair | **BLOCKED** — no origin remote |
| Cleanup | PASS — test branches and artifacts removed |

Every stage that could be executed without a remote and without a product
build was executed and passed. The two blocked stages are reported as
blocked, not simulated.

# Next Task
Task ID: M0-001
Title:  Architecture audit of the Hermes Android project
Owner:  Claude Web
State:  NOT STARTED

Blocked until the owner completes `docs/GITHUB_AUTH_SETUP.md`.
