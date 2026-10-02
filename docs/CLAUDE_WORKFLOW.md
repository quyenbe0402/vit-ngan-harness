# Claude Web Workflow

Claude's standard operating procedure. Twelve steps, in order, every time.

---

## Preconditions

A single, identified task. Not two. Not "and also".

---

## 1. Receive one task

Confirm the task ID and the requirements document it comes from. If the
request contains multiple tasks, ask which one to do first.

## 2. Read the master requirements

Read the full requirement for this task, not a summary of it. Note the
constraints — especially anything that limits scope.

## 3. Inspect the repository state

Know what exists. Do not assume a file, a module, or a convention is there
because it "should be". In MODE B, ask for `DEVELOPMENT_STATUS.md` and the
relevant file contents if they are not in context.

## 4. Read the current development status

`DEVELOPMENT_STATUS.md` is the shared memory of the project. Read it before
planning anything. It tells you what the previous task actually achieved —
which may differ from what that task's handoff claimed.

Also read Cline's most recent integration report if one exists. Its
"Known Issues" section is the real input to the next task.

## 5. Inspect the relevant architecture

Read the design documents and the existing code that this task touches.
Follow the conventions already present. Consistency with the existing
codebase matters more than personal preference.

## 6. Implement the task

- Stay inside the task's scope
- Write unit tests in the same task
- If the task requires an architecture change, stop and raise a PROPOSAL
- If the task reveals a much larger problem, report it as a finding with a
  proposed new task ID — do not silently expand

## 7. Run focused tests

Run the tests that cover what changed. If there is no execution environment,
record `TESTS NOT RUN IN THIS ENVIRONMENT` — accurately. Do not write
"tests pass" in a report when nothing was executed.

## 8. Review the diff

Read the actual diff. Check for:

- unrelated changes
- debug statements, TODOs, commented-out code
- hardcoded absolute paths or personal machine paths
- anything that looks like a credential
- changes to files the task did not name

## 9. Update the status

Update `DEVELOPMENT_STATUS.md` with the Claude-side fields: milestone,
task, branch, commit, Claude status, architecture impact, next task.
Honesty is the point — if device validation is pending, say it is pending.

## 10. Commit

```
git commit -m "<type>(<task-id>): <what changed and why>"
```

Keep the working tree clean. The status file update is committed, not left
in the working directory.

## 11. Push

**MODE A:**

```
git push --set-upstream origin claude/<task-id>-<short-name>
```

**MODE B:** you cannot push. Produce the patch or file set, state clearly
that the commit does not yet exist, and tell the user exactly what to run.
See `docs/CLAUDE_GITHUB_ACCESS.md`.

## 12. Write the handoff report

Use `docs/REPORT_FORMAT.md`. Include every field from
`docs/HANDOFF_PROTOCOL.md` §3. Save to
`handoff/outgoing/<task-id>-handoff.md`.

---

## The rule that matters most

**Claude must not claim that physical-device validation passed unless it
actually ran in an environment capable of doing so.**

Claude has no device. It cannot install an APK, cannot read Logcat, and
cannot observe runtime behaviour. Any device result in a Claude report is
**Cline's measurement, relayed by Claude, attributed to Cline.**

The exact wording to use:

> Physical Device: NOT TESTED by Claude. Awaiting Cline validation on the
> physical device.

Not "expected to work". Not "should work on device". Not "PASS".
