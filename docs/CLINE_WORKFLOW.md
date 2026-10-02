# Cline Desktop Workflow

Cline's standard operating procedure. Twenty steps, in order, every time.

---

## Preconditions

Before starting: a Claude branch has been handed off, and
`DEVELOPMENT_STATUS.md` names it.

---

## 1. Fetch

```powershell
.\scripts\git-fetch-all.ps1
```

Never work from a stale local copy. Always fetch first.

## 2. Identify the target Claude branch

Read the task ID from `DEVELOPMENT_STATUS.md` or the handoff report. The
branch will be `claude/<task-id>-<short-name>`.

## 3. Check out / fetch the branch

```powershell
.\scripts\git-check-branch.ps1 -Branch claude/M0-001-audit
git checkout claude/M0-001-audit
```

If the working tree is dirty, resolve that **first**. Do not discard
uncommitted work without understanding it.

## 4. Inspect the commit

```powershell
git --no-pager log --oneline -5
git --no-pager show --stat HEAD
```

Read the commit message. Does it describe what you are about to validate?

## 5. Inspect the diff

```powershell
git --no-pager diff origin/develop...HEAD
```

Read the entire diff. Cross-check the changed-file list against the
handoff's declared file list. A discrepancy is a finding to report.

## 6. Inspect `DEVELOPMENT_STATUS.md`

```powershell
Get-Content DEVELOPMENT_STATUS.md
```

Does the file describe the state you are actually looking at? If it claims
validation that has not happened, treat that as a defect in the handoff.

## 7. Run the build

```powershell
.\scripts\build.ps1
```

Record: `PASS`, `FAIL`, or `NOT CONFIGURED`.

## 8. Run the tests

```powershell
.\scripts\test.ps1
```

Record actual pass/fail counts, not impressions.

## 9. Connect the Android device

```powershell
adb devices
```

Expected: a serial with state `device`. If the state is `unauthorized`,
accept the RSA prompt on the phone. If the list is empty, the state is
`NOT_CONNECTED` and steps 10–12 are reported as `NOT TESTED`.

Physical device via USB-C is the primary target. An emulator may be used for
a quick check, but it never substitutes for device validation.

## 10. Install

```powershell
.\scripts\install-device.ps1 -PackageName <app-id> -UninstallFirst
```

The application ID is a parameter. It is not hardcoded in the scripts
because the Android project does not exist yet.

## 11. Launch

```powershell
.\scripts\launch-device.ps1 -PackageName <app-id> -ClearLogcat -ForceStop
```

Clearing Logcat *before* launch is deliberate — it means the capture contains
only this run, so a startup crash cannot be missed.

## 12. Collect logs

```powershell
.\scripts\collect-logcat.ps1 -DurationSeconds 20
```

For a hang or a slow failure, capture more, or capture live:

```powershell
.\scripts\collect-logcat.ps1 -Live
```

## 13. Exercise the app manually

Scripts launch the app. They do not use it. A human must actually perform
the task the app is supposed to perform, on the real device, and observe
what happens: permissions, lifecycle, background behaviour, and anything the
build and unit tests cannot see.

## 14. Diagnose

If something failed, find the cause from evidence — the compiler output, the
test report, or the Logcat stack trace. Categorise it:

| Category | Action |
|----------|--------|
| Implementation defect, in scope | Repair it |
| Implementation defect, out of scope | Report; do not fix |
| Architecture problem | Escalate to Claude; do not fix |
| Missing prerequisite | Report BLOCKED |

## 15. Repair (only if in scope)

```powershell
.\scripts\git-create-branch.ps1 -Agent cline -TaskId M0-001 -ShortName fix-launch -Base claude/M0-001-audit
```

Fix the defect. Add a test that would have caught it. Change nothing else.

## 16. Re-run tests

```powershell
.\scripts\build.ps1
.\scripts\test.ps1
```

A repair that has not been re-validated is not a repair.

## 17. Re-run device validation

```powershell
.\scripts\full-device-test.ps1 -PackageName <app-id>
```

One command runs build, tests, install, launch, log capture, and writes a
summary report to `handoff/reports/`.

## 18. Update `DEVELOPMENT_STATUS.md`

Record the **measured** results: build status, test status, device status,
known issues, blocked issues. Every claim must trace to something that was
actually executed.

## 19. Commit

```powershell
git add -A
git commit -m "fix(M0-001): <what was wrong and what was changed>"
```

## 20. Push

```powershell
.\scripts\git-push-branch.ps1 -Branch cline/M0-001-fix-launch
```

## 21. Write the integration report

Use `docs/REPORT_FORMAT.md`. Save to
`handoff/reports/M0-001-integration.md`. Reference the Logcat file and the
device test summary by filename so the evidence is findable.

---

## When something is BLOCKED

Report BLOCKED and stop. Do not proceed to later steps and present an
incomplete validation as a completed one. A blocked report is a valid,
useful outcome. A faked pass is not.

---

## Quick reference

```powershell
# 1-6: sync and inspect
.\scripts\git-fetch-all.ps1
.\scripts\git-check-branch.ps1 -Branch claude/M0-001-audit

# 7-8: build and test
.\scripts\build.ps1
.\scripts\test.ps1

# 9: device presence
adb devices

# 10-12, 17: full device loop
.\scripts\full-device-test.ps1 -PackageName <app-id>

# 15: repair branch
.\scripts\git-create-branch.ps1 -Agent cline -TaskId M0-001 -ShortName fix-x -Base claude/M0-001-audit

# 20: push
.\scripts\git-push-branch.ps1 -Branch cline/M0-001-fix-x
```
