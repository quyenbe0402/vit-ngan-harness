# Handoff Protocol

The handoff is the moment control passes from one agent to the other. It is
the only thing standing between "Claude wrote some code" and "the work is
part of the project's real state".

A handoff is **complete** only when the receiving agent can act on it
without asking a question.

---

## 1. The two handoff directions

### Direction 1 — CLAUDE → CLINE (implementation handoff)

```
CLAUDE
  -> implementation complete
  -> tests written
  -> commit created
  -> branch pushed to GitHub
  -> handoff metadata written
  |
  v
CLINE
  -> fetch
  -> inspect
  -> build
  -> test
  -> validate
  -> repair
  -> test again
  -> device validation
  -> commit
  -> push
  -> integration report
  |
  v
CLAUDE
  -> reads Cline result
  -> continues with the next task
```

### Direction 2 — CLINE → CLAUDE (validation result)

Cline does not just fix things. It reports **what it found**, so that Claude
can decide what the next task is. An unreported failure is worse than a
failure: it leaves the project believing in something unverified.

---

## 2. Preconditions — Claude may not hand off until all are true

- [ ] Implementation is complete for the stated task
- [ ] Unit tests are written for the new behaviour
- [ ] The branch is named `claude/<task-id>-<short-name>`
- [ ] A commit exists (in MODE A, pushed; in MODE B, the user applies and commits)
- [ ] `DEVELOPMENT_STATUS.md` has been updated and committed
- [ ] The handoff report exists, using `docs/REPORT_FORMAT.md`
- [ ] Anything Claude could **not** do is stated explicitly

**If any of these is false, the handoff is not ready.** Say so rather than
handing off a half-finished task.

---

## 3. Required handoff fields

Every handoff carries these. A field that does not apply is written as
`N/A` with a reason — never left blank, never invented.

| Field | Meaning | Notes |
|-------|---------|-------|
| **Task ID** | e.g. `M0-001` | Convention in section 7 |
| **Branch** | e.g. `claude/M0-001-audit` | Must match what was actually pushed |
| **Base branch** | e.g. `develop` | What the branch was cut from |
| **Commit** | Full or short SHA | In MODE B before the user commits: `NOT YET COMMITTED` |
| **Files changed** | List of repo-relative paths | Not prose — actual paths |
| **Tests run** | What was executed | e.g. `gradlew testDebugUnitTest` |
| **Tests passed** | Count / names | Measured, not estimated |
| **Tests failed** | Count / names, with failure reason | `0` if genuinely none |
| **Known issues** | Anything unresolved | Empty list is acceptable; a lie is not |
| **Architecture impact** | `NONE` or `PROPOSAL` | See below |
| **Next task** | What Claude intends to do next | Subject to reading Cline's result |
| **Validation status** | Who has validated what | See the table below |

### Validation status vocabulary

| Value | Meaning |
|-------|---------|
| `NOT VALIDATED` | Nobody has built or run it yet |
| `BUILD PASS / TESTS PASS` | Cline ran it; device not yet involved |
| `DEVICE PASS` | Installed, launched and exercised on the physical phone |
| `DEVICE FAIL` | Installed but behaved incorrectly, or crashed |
| `BLOCKED` | Could not be validated; prerequisite missing |
| `NOT TESTED` | Deliberately not tested, with a stated reason |

**Architecture impact** must be one of:

- `NONE` — implementation matches the agreed design
- `PROPOSAL` — implementation required a design change, described in full,
  which requires the owner's decision. A proposal is **not** a change that
  has already been made unilaterally.

---

## 4. Preconditions — Cline may not report until all are true

- [ ] Fetched; working tree is on the expected branch at the expected commit
- [ ] Inspected the diff and confirmed it matches the handoff's file list
- [ ] Build attempted; result recorded as PASS or FAIL
- [ ] Tests attempted; result recorded as PASS or FAIL
- [ ] Device state determined honestly (connected, or `NOT_CONNECTED`)
- [ ] If connected: installed, launched, exercised, logs collected
- [ ] Any repair re-validated
- [ ] `DEVELOPMENT_STATUS.md` updated and committed
- [ ] Integration report written using `docs/REPORT_FORMAT.md`
- [ ] Repair branch pushed (if repairs were made)

---

## 5. Rules that apply to both directions

1. **No silent gaps.** If something was not done, write it down. A handoff
   with an honest "not done" is usable; one with a hidden gap is not.
2. **No invented results.** A SHA, a test count, or a device outcome that
   was not observed is a fabrication, regardless of intent.
3. **No uncommitted state as truth.** Anything that matters is committed and
   pushed. If it is not on GitHub, it did not happen.
4. **Attribution is explicit.** Device results belong to Cline. Claude may
   relay them but must not claim them.
5. **One task at a time.** Do not batch unrelated tasks into one handoff.
6. **Status file is updated by both**, and every update is committed.

---

## 6. Where handoff artifacts live

| Artifact | Location |
|----------|----------|
| Live status | `DEVELOPMENT_STATUS.md` (committed) |
| Handoff report (outgoing) | `handoff/outgoing/<task-id>-handoff.md` |
| Handoff report (incoming, when read) | `handoff/incoming/<task-id>-handoff.md` |
| Integration report | `handoff/reports/<task-id>-integration.md` |
| Logcat capture | `handoff/reports/logcat-<serial>-<timestamp>.txt` |
| Device test summary | `handoff/reports/device-test-<timestamp>.txt` |
| Closed handoffs | `handoff/archive/<task-id>/` |

Rules and rationale: `docs/HANDOFF_DIRECTORY.md`.

**Nothing in `handoff/` is the source of truth.** It is a transport area.
The truth is the GitHub branch and commit.

---

## 7. Task ID convention

```
M<milestone>-<sequence>

Examples:
  M0-001     milestone 0, task 1
  M0-002     milestone 0, task 2
  M1-001     milestone 1, task 1
  M1-002     milestone 1, task 2
```

- Format is enforced by `-ValidatePattern` in `scripts/git-create-branch.ps1`
- Task IDs are **allocated lazily**. Only the current task and the next
  planned one have IDs. Do not pre-generate a backlog of hundreds.
- An ID is never reused. A cancelled task keeps its ID and is marked
  `CANCELLED` in the status file.
- Milestones are declared in the requirements document. `M0` is the
  environment/architecture phase this repository is currently in.

