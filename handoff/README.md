# Handoff Directory

The transport area between Claude Web and Cline Desktop.

**Nothing here is the source of truth.** The source of truth is GitHub:
branch → commit → pull request → merged history. A file in `handoff/`
*describes* work; a commit on GitHub *is* work.

---

## Layout

```
handoff/
├── README.md          this file
├── claude/            handoffs written BY Claude FOR Cline
├── cline/             validation results written BY Cline FOR Claude
├── reports/           evidence: integration reports, logcat, device summaries
├── incoming/          artifacts Cline has received (working copies)
├── outgoing/          artifacts Claude has produced
└── archive/           closed handoffs, kept for history
```

`claude/` and `cline/` are the primary convention. `incoming/`, `outgoing/`
and `archive/` exist from the earlier setup and remain valid — they hold
working copies and history respectively. Nothing needs to move.

---

## Claude writes

`handoff/claude/<task-id>.md`

```markdown
# Task
Task ID:    M0-003
Title:      <one line>
Branch:     claude/M0-003-<short-name>
Base:       develop
Commit:     <sha, or "NOT YET COMMITTED" in MODE B>
Handoff mode: MODE A | MODE B

## Task objective
What this change accomplishes, in one or two sentences.

## Specification references
Which sections of the master specification this implements.

## Files changed
- path/to/File.kt    (added)
- path/to/Other.kt   (modified)
Total: N files, +X / -Y

## Architecture decisions
What was chosen, what was rejected, and why. Or "None - no architectural
change".

## Tests expected
What Cline should run, and what a pass looks like.
- gradlew testDebugUnitTest   -> N new tests
- gradlew assembleDebug      -> APK at app/build/outputs/apk/debug/
- device check               -> grant permissions, then <specific flow>

## Known limitations
What this task deliberately does NOT do.

## Remaining risks
What might fail on a real device that could not be checked here.

## Validation instructions
Exact steps Cline must follow, in order, to confirm this works.
```

---

## Cline writes

`handoff/cline/<task-id>-validation.md`

```markdown
# Task
Task ID:    M0-003
Validating: claude/M0-003-<short-name> @ <sha>
Device:     <model, Android version, SDK, ABI>
Validated by: Cline Desktop
Date:       YYYY-MM-DD

## Build result
PASS | FAIL | NOT CONFIGURED
Command: ./scripts/build.ps1
Detail: <errors, or why not run>

## Tests
PASS | FAIL | NOT RUN
Command: ./scripts/test.ps1
Run: N   Passed: N   Failed: N
Detail: <failure output, or why not run>

## Device result
PASS | FAIL | NOT TESTED
Command: ./scripts/full-device-test.ps1
Detail: <what was observed on the phone; NOT_CONNECTED if no device>

## Bugs discovered
- <symptom> -> <root cause>
- or "None"

## Fixes made
- <file> : <what changed and why>
- Regression test added: <test name>
- Or "None - no repair was in scope"

## Regression status
What was re-run after the repair, and its result.

## Commit hash
<sha of the repair commit on cline/<task-id>-<short-name>>
Or "NO REPAIR MADE".

## Remaining blockers
- or "None"
```

---

## Rules

1. **Factual status only.** `PASS` means it was run and passed. `NOT TESTED`
   means it was not run. The middle ground of leaving a field blank is not
   available.
2. **No secrets.** Never a token, key, password, or keystore password. This
   directory is committed. See `docs/SECRETS_POLICY.md`.
3. **Raw device logs stay out of Git.** `logcat-*.txt` is gitignored because
   it can contain fragments of user data. Commit the *summary lines* into the
   validation report instead.
4. **Name files by task ID.** `<task-id>.md` and
   `<task-id>-validation.md`. Stable names matter — reports reference each
   other by path.
5. **Archive when closed.** Move both directions into `archive/<task-id>/`
   once the task is finished.

---

## Task ID convention

```
M<milestone>-<sequence>          e.g. M0-001, M1-002
```

Allocated lazily — only the current and next planned task have IDs. Never
reused. See `docs/HANDOFF_PROTOCOL.md`.
