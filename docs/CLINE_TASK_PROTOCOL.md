# Cline Task Protocol

How Cline Desktop executes a validation/integration task, from receipt to
report.

---

## The lifecycle

```
RECEIVE -> SYNC -> INSPECT -> BUILD -> TEST -> DEVICE TEST
        -> DIAGNOSE -> REPAIR -> RETEST -> VERIFY -> COMMIT -> PUSH -> REPORT
```

Cline is the agent that **measures**. Every stage produces an observation,
not an opinion.

---

## RECEIVE

**Do:** identify which task and which Claude branch is to be validated.

**Do not:** start validating a branch that was never handed off.

---

## SYNC

```powershell
.\scripts\git-fetch-all.ps1
.\scripts\git-check-branch.ps1 -Branch claude/<task-id>-<short-name>
```

Fetch first. Never validate a stale local copy.

**Exit condition:** the remote branch is known locally, and the expected
commit is identified.

---

## INSPECT

- Check out the branch (or inspect it without switching, if the working tree
  has unrelated changes)
- `git log` — read the commit message
- `git diff` — read the **whole** diff
- Compare the changed-file list against the handoff's file list. A mismatch
  is a finding, not a detail.
- Read `DEVELOPMENT_STATUS.md` on that branch

**Exit condition:** Cline knows what was supposed to change, and can see
what actually changed.

---

## BUILD

```powershell
.\scripts\build.ps1
```

Record PASS, FAIL, or `NOT CONFIGURED`. A build failure here is data, not
an obstacle to work around.

**Exit condition:** the build result is recorded.

---

## TEST

```powershell
.\scripts\test.ps1
```

Runs lint and the unit tests. Record the actual counts.

**Exit condition:** test results are recorded, including the number of
failures and what they were.

---

## DEVICE TEST

```powershell
adb devices
.\scripts\install-device.ps1 -PackageName <app-id>
.\scripts\launch-device.ps1  -PackageName <app-id> -ClearLogcat
.\scripts\collect-logcat.ps1 -DurationSeconds 20
```

The primary test environment is a **physical phone connected over USB-C**.

If no device is connected, the result is `NOT_CONNECTED` and device
validation is `NOT TESTED`. Do not substitute an emulator and do not report
a result that was not observed.

**Exit condition:** the device state is honestly recorded.

---

## DIAGNOSE

When something failed, establish **where** it failed and **why**, from
evidence:

- Build error → read the actual compiler/linker output
- Test failure → read the test report, not just the failure count
- Crash → read Logcat; find the stack trace and the cause
- Wrong behaviour → reproduce it deliberately, then narrow it

Distinguish clearly between:

- a **defect in the implementation** → repairable by Cline
- an **architecture problem** → escalate to Claude, do not fix
- a **missing prerequisite** (no device, no SDK) → report BLOCKED

**Exit condition:** the failure has a cause and a category.

---

## REPAIR

**Only for implementation-level defects within the task's scope.**

```powershell
.\scripts\git-create-branch.ps1 -Agent cline -TaskId <task-id> -ShortName <short-name> -Base claude/<task-id>-<short-name>
```

A repair:
- fixes the defect
- does not redesign anything
- does not add features
- adds or updates a test that would have caught the defect

If the fix would require an architecture change, stop and escalate. That is
Claude's decision, not Cline's.

**Exit condition:** the defect is fixed and covered by a test.

---

## RETEST

Re-run everything that was run before:

```powershell
.\scripts\build.ps1
.\scripts\test.ps1
.\scripts\full-device-test.ps1 -PackageName <app-id>
```

**A repair is not done until it has been re-validated.** An un-retested
repair is a guess.

**Exit condition:** the previously failing stage now passes, or the failure
is documented as still failing.

---

## VERIFY

- Confirm the branch state is clean
- Confirm `DEVELOPMENT_STATUS.md` reflects reality
- Confirm no secrets were introduced
- Confirm no files outside the repair scope changed

**Exit condition:** the working tree reflects exactly the intended change.

---

## COMMIT

- Commit on `cline/<task-id>-<short-name>`
- Message states the defect, the cause, and the fix
- Reference the task ID
- Include the updated `DEVELOPMENT_STATUS.md`

**Exit condition:** the commit exists and the tree is clean.

---

## PUSH

```powershell
.\scripts\git-push-branch.ps1 -Branch cline/<task-id>-<short-name>
```

Never push to `main` or `develop`. Never force-push over Claude's work.

**Exit condition:** the branch is on GitHub, or the push failure is
reported honestly as BLOCKED.

---

## REPORT

Write the integration report using `docs/REPORT_FORMAT.md` to
`handoff/reports/<task-id>-integration.md`.

Attach or reference the Logcat capture and the device test summary from
`handoff/reports/`.

**Exit condition:** Claude can read the report and know exactly what the
real state is, without re-running anything.

---

## Hard rules for Cline

1. **Never** report a test as passing unless it was run and passed.
2. **Never** report a device result that was not observed.
3. **Never** report `DEVICE PASS` when no device was connected.
4. **Never** fix an architecture problem unilaterally.
5. **Never** hide a failure to make a report look cleaner. A red report
   that is accurate is worth more than a green one that is not.
6. **Never** push to `main`, force-push, or rewrite Claude's history.
7. **Never** ask for or handle credentials.
