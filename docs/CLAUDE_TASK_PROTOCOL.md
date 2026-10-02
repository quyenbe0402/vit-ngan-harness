# Claude Task Protocol

How Claude Web executes a single task, from receipt to handoff.

---

## The lifecycle

```
RECEIVE -> READ -> PLAN -> IMPLEMENT -> TEST -> REVIEW -> COMMIT -> PUSH -> HANDOFF
```

Each stage has an exit condition. If a stage cannot be completed, Claude
says so at that point rather than skipping ahead and reporting success at
the end.

---

## RECEIVE

**Do:** confirm the task ID, the requirements it comes from, and the base
branch.

**Do not:** start work on a task that was not assigned, or accept two tasks
at once.

**Exit condition:** the task is identified, and its source in the
requirements document is named.

---

## READ

Read, in this order:

1. The requirements / master plan for the task
2. `DEVELOPMENT_STATUS.md` — what is the current real state?
3. The relevant architecture documentation
4. The existing code that the task touches
5. Cline's most recent integration report, if the task follows a repair

**Why this order:** implementing before reading the status file is how
work gets duplicated or contradicts a previous decision.

**Exit condition:** Claude can state, in its own words, what the current
state is and what the task changes.

---

## PLAN

State:

- Which files will change
- What the approach is
- What could go wrong
- What the tests will assert
- Whether this affects the architecture

If the plan requires an architectural change, stop and raise it as a
**PROPOSAL** before implementing. Do not implement the change and mention
it afterwards.

**Exit condition:** a concrete plan exists and, if it touches architecture,
owner approval exists.

---

## IMPLEMENT

- Write the code
- Write the unit tests **in the same task**, not later
- Follow the conventions already in the repository — do not introduce a new
  style, formatter, or dependency layout without saying so
- Keep the change scoped to the task

**Exit condition:** the feature behaves as planned, and tests exist that
would fail if it were broken.

---

## TEST

Run the tests that can actually be run in Claude's environment.

If Claude has no execution environment, the correct action is to say
`TESTS NOT RUN IN THIS ENVIRONMENT` and hand off — **not** to claim the
tests pass. Cline will run them.

A test that has never been executed is an unverified test.

**Exit condition:** every test that was run has a recorded result, and any
test that was not run is explicitly listed as not run.

---

## REVIEW

Before committing:

- `git diff` — read the actual diff, not the intent
- Confirm the change set matches what the plan said
- Confirm no secrets, no credentials, no absolute personal paths, no
  debug leftovers
- Confirm no unrelated files were touched
- Confirm no product code was modified outside the task's scope

**Exit condition:** the diff is something you would defend in review.

---

## COMMIT

- Commit on `claude/<task-id>-<short-name>`
- Write a message that states *what changed and why*, in the imperative
- Reference the task ID in the message
- Update `DEVELOPMENT_STATUS.md` **in the same commit or a preceding one** —
  never leave the status file describing a state that is not committed

**Exit condition:** the working tree is clean and the commit exists.

---

## PUSH

**MODE A** (Git-enabled environment):

```
git push --set-upstream origin claude/<task-id>-<short-name>
```

**MODE B** (chat-only): Claude cannot push. It produces the patch or file
set and hands it to the user, stating clearly that the commit does not yet
exist. See `docs/CLAUDE_GITHUB_ACCESS.md`.

**Never** claim a push happened without observing it succeed.

**Exit condition:** the branch exists on GitHub, or the user has been
explicitly instructed on what to apply and commit.

---

## HANDOFF

Write the report using `docs/REPORT_FORMAT.md`, including every required
field from `docs/HANDOFF_PROTOCOL.md` §3.

Store it at `handoff/outgoing/<task-id>-handoff.md`.

**Exit condition:** Cline could act on this handoff without asking a
question.

---

## Hard rules for Claude

1. **Never** claim physical-device validation. Claude has no device. If
   device results appear in the report, they are Cline's, attributed to
   Cline.
2. **Never** invent a commit SHA, a test count, or a file path.
3. **Never** work on `main` or `develop` directly.
4. **Never** ask for, accept, or transmit a credential.
5. **Never** silently expand scope. If the task revealed a bigger problem,
   report it as a finding and propose a separate task ID.
6. **Never** begin the next task without reading Cline's result first.
