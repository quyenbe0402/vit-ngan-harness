# Report Format

Every agent report — Claude handoffs and Cline integration reports alike —
uses this structure. Consistency here is what makes a report readable by
the other agent without a conversation.

---

## Template

```markdown
# Task
Task ID: M0-001
Title:  <one line>
Agent:  Claude | Cline

# Agent
Claude Web | Cline Desktop
Date: YYYY-MM-DD

# Branch
Branch:       claude/M0-001-audit
Base branch:  develop
Remote:       origin

# Commit
Commit: <full SHA>
       <short SHA>
Status: PUSHED | NOT YET COMMITTED | BLOCKED

# Files Changed
- path/to/file.kt          (modified)
- path/to/NewFile.kt       (added)
- path/to/OldFile.kt       (deleted)
Total: N files, +X / -Y lines

# Build
Result: PASS | FAIL | NOT RUN | NOT CONFIGURED
Command: ./gradlew assembleDebug
Notes: <errors, or why not run>

# Tests
Result: PASS | FAIL | NOT RUN
Command: ./gradlew testDebugUnitTest
Run:      N
Passed:   N
Failed:   N
Notes: <failure details, or "not executed in this environment">

# Physical Device
Result: PASS | FAIL | NOT TESTED
Device: <manufacturer model, Android version, SDK, ABI> | NOT_CONNECTED
Command: scripts/full-device-test.ps1
Notes: <observed behaviour; Logcat file reference>

# Repairs
<none, or: what was broken / root cause / what was changed / test added>

# Known Issues
<none, or a list with enough detail to act on>

# Architecture Impact
NONE | PROPOSAL
<if PROPOSAL: what needs to change, why, and what decision is required>

# Next Task
Task ID: M0-002
Title:  <one line>
```

---

## Field rules

These are not formatting preferences. Each one prevents a specific,
recurring failure.

### Task
Always the ID from the convention `M<milestone>-<sequence>`. A report
without a task ID cannot be matched to the work.

### Agent
`Claude` or `Cline`. This determines who is accountable for the claims
below.

### Branch
The branch that was actually used. Not the branch that was intended.

### Commit
The SHA that actually exists.

- If nothing has been committed yet: `NOT YET COMMITTED`. This is a normal
  and acceptable value in **MODE B**, where the user performs the commit
  after applying a patch.
- **Never invent a SHA.** A plausible-looking hash that does not exist is
  the single most damaging thing a report can contain, because the other
  agent will try to check it out.

### Files Changed
Actual paths, relative to the repository root. Not prose descriptions.
"Updated the bridge implementation" is not a file list.

### Build
One of: `PASS`, `FAIL`, `NOT RUN`, `NOT CONFIGURED`.

`NOT CONFIGURED` is used when the Android project does not exist yet — it
is distinct from `FAIL` and is not a failure.

### Tests
Give counts, not adjectives. "Tests pass" is unverifiable; "34 run, 34
passed, 0 failed" is checkable.

If the tests were not executed, the result is `NOT RUN` and the notes say
so. A report must never imply a test run that did not happen.

### Physical Device
One of: `PASS`, `FAIL`, `NOT TESTED`.

- `NOT TESTED` when no device was connected, or when the agent has no device
  capability (which is always the case for Claude).
- The device identity line is filled in from `adb shell getprop`, not from
  assumption.

**Only Cline may report a device result from observation.** Claude may
relay one, but must attribute it. If Claude did not run it, the line reads:

> Physical Device: NOT TESTED by Claude. Awaiting Cline validation.

### Repairs
`none` is a valid and complete answer. When repairs exist, state the
symptom, the root cause, the change, and the test that now covers it. A
repair without a regression test is incomplete.

### Known Issues
`none` is valid. An empty list is honest; a hidden issue is not. Include
enough detail that Claude can decide whether to make it the next task.

### Architecture Impact
`NONE` or `PROPOSAL`.

`PROPOSAL` means a design change is needed and has **not** been made. If the
change was already made unilaterally, that is a protocol violation, and it
should be declared as a known issue.

### Next Task
The next task ID and title. Cline proposes it based on what it found;
Claude confirms it after reading the report.

---

## Storage locations

| Report type | Path |
|-------------|------|
| Claude handoff | `handoff/outgoing/<task-id>-handoff.md` |
| Cline integration | `handoff/reports/<task-id>-integration.md` |
| Logcat evidence | `handoff/reports/logcat-<serial>-<timestamp>.txt` |
| Device test summary | `handoff/reports/device-test-<timestamp>.txt` |

Reports in `handoff/` are working artifacts. The durable record is the Git
commit and `DEVELOPMENT_STATUS.md`.

---

## Length

As long as it takes to be complete, and no longer. A report padded with
restated requirements is harder to read, not safer. Every line should carry
information the other agent did not already have.
