# Clean APIs — Access Mode Decision

**Purpose:** determine whether Claude Web (via Clean APIs Playground) has
real shell/filesystem access, and hand over the correct operating procedure
for that answer.

**Status:** awaiting one test result from the owner.

---

## Why this document exists

Everything else in the development platform is ready and verified: Git,
GitHub, `main` + `develop`, authentication, the physical device, the
scripts, the CI skeleton, and the handoff structure.

The single unknown is **how Claude can reach the repository**. That answer
determines the entire operating procedure, so it is worth one careful test
rather than a guess.

This is the formal version of `docs/CLAUDE_GITHUB_ACCESS.md`. That document
defines the two modes in general; this one is the decision procedure for
this project.

---

## The core distinction

Clean APIs is an **API/model gateway**. It provides OpenAI-compatible
endpoints and tool calling. Tool calling lets a model *request* that a
program perform an action — but the model still needs an environment with
filesystem, Git, and credentials to actually perform it.

```
Clean APIs  =  model transport          GitHub  =  repository truth
```

These are **not connected** merely because Claude is running through
Clean APIs. There is no "link GitHub to Clean APIs" step, and a working
API connection is **not evidence** of repository write access.

---

## The test

Paste this into the Playground **verbatim**:

```text
Do not simulate or guess any output.

Check whether you have real shell/filesystem execution.

If you have a shell, execute each of these in turn:

  git --version
  git remote -v
  pwd

Then report:
1. Which tool you used to run the commands.
2. The verbatim output of each command.
3. Whether you actually accessed a filesystem / Git repository.

If you do not have shell execution in this environment, reply exactly:
"I do not have shell/filesystem execution in this environment."

Do not fabricate output.
```

### Optional stronger follow-up

A model can fabricate plausible output for a one-shot test. To make
fabrication harder, run this second turn **in the same conversation**:

```text
Create a file named after a random 8-character string you invent, write
your own device's timestamp into it, then list the directory showing the
filename and its contents. Report the filename verbatim.
---

## Reading the result

| Observation | Verdict |
|-------------|---------|
| A visible tool-call / tool-execution trace in the UI, returning real output | **MODE A** |
| Claude states it has no shell execution | **MODE B** |
| Prose answer describing what output *would* be, with no tool trace | **MODE B** |
| Claude "runs" commands but the outputs look invented or generic | **MODE B** |
| Second-turn filename test is consistent | **MODE A** |
| Second-turn filename differs or is forgotten | **MODE B** |

**The key evidence is a tool-execution trace, not the content of the
answer.** A model can write `git version 2.55.0.windows.3` from training
data. It cannot produce a tool trace it never emitted.

---

## MODE A — Claude has real shell and Git access

Claude can read and modify the repository directly.

### Verification before trusting it

```
git ls-remote https://github.com/quyenbe0402/vit-ngan-harness.git
```

Then a write test on a **throwaway branch only** — never `main`:

```
git checkout -b claude/M0-000-authcheck
# create a small temporary file
git add -A && git commit -m "test: verify Claude can push"
git push -u origin claude/M0-000-authcheck
git push origin --delete claude/M0-000-authcheck   # clean up
```

MODE A is only confirmed once a real push succeeds. Everything before that
is a capability claim, not a verified fact.

### Procedure

1. Claude works directly on `claude/<task-id>-<short-name>`
2. Claude commits and pushes
3. Cline fetches, builds, tests, validates on device
4. Cline repairs on `cline/<task-id>-<short-name>`
5. Repeat

Full procedure: `docs/DEVELOPMENT_LOOP.md`.

### Constraint that still applies

MODE A does **not** exempt Claude from the device rule. Claude has no
physical Android device and must never report device validation. That
remains Cline's exclusive responsibility.

---

## MODE B — Claude is chat-only (no repository write access)

**This is the default assumption.** Plan for MODE B until MODE A is proven
by an observed push.

Claude still does the intellectually primary work: architecture, planning,
code, tests, and diagnosis. It simply cannot touch the filesystem.

### Procedure

```
Claude (Clean APIs)
   ↓  produces a patch or a file set
   v
Owner applies it  (B1: git apply  |  B2: transfer files)
   v
Owner commits and pushes
   v
GitHub
   ↓
Cline Desktop  — fetch, build, test, device, repair
   v
Physical Android phone (USB-C / ADB)
   v
Cline pushes fixes → GitHub → Claude reads the report
```

### Handoff mechanics

**B1 — patch (best for surgical changes)**

```
git apply --check <patch>   # MUST pass first
git apply <patch>
git commit -m "<task-id>: <what changed>"
```

**B2 — file transfer (best for new or rewritten files)**

Claude states the exact repository-relative path and complete content for
every file. The owner writes them, then runs `git status` and `git diff` to
confirm the change set matches what Claude described.

### Mode discipline

Claude must state its mode explicitly in every handoff. A handoff from
MODE B carries **no commit SHA** — it carries the tree the owner should
produce. An invented SHA is the single most damaging thing a report can
contain, because the other agent will try to check it out.

Templates: `handoff/README.md`.
Field definitions: `docs/HANDOFF_PROTOCOL.md` §3.

---

## Verification checklist before M0 begins

- [ ] Test pasted into the Playground, result reported
- [ ] Mode recorded as A or B in `DEVELOPMENT_STATUS.md`
- [ ] If A: real push observed on a throwaway branch, then deleted
- [ ] If B: handoff template confirmed with the owner
- [ ] Owner knows they perform the `git apply` / file-write step

Only then is the M0 prompt written — and it differs by mode.
```

Then ask, in a **third** turn: "What is the filename you created?"

A model fabricating output must invent the same random filename twice. If
it is consistent, that is strong evidence of a real filesystem. If it
invents a different name, or claims it cannot remember, that is evidence of
MODE B.