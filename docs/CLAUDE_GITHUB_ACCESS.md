# Claude Web and GitHub Access

**Purpose:** state, without ambiguity, what Claude Web can and cannot do in
this workflow, and exactly where the Git operation happens.

## The one thing to get right

**Do not assume that a browser chat connected to an API gateway has GitHub
write access.**

### Clean APIs is not GitHub access

**Clean APIs is a model/API transport layer.** It describes how a request
reaches a model — the transport, not the permissions. Knowing that the model
is reachable tells you nothing about whether the surrounding environment has:

- a Git client
- a checkout of this repository
- a configured `origin`
- working credentials

These are four separate facts. Only the fourth is even about access, and
only a real push proves it.

**A working API connection is not proof of repository write access. Not
"probably", not "likely" — not proof at all.**

Therefore, before planning any handoff, answer this question explicitly:

> In Claude's environment, can it run `git commit` and `git push` against a
> real remote?

If the answer is not a verified **yes**, the handoff is in **MODE B**, and
MODE B has four legitimate mechanisms (section 4). Only one of them is
"Claude pushes directly", and it is not the default.

---

## 1. What Claude Web CAN do

- Read and understand requirements and plans
- Design and propose architecture
- Write source code, tests, documentation, and configuration
- Review existing code and explain it
- Produce patches, file contents, and complete file sets
- Reason about failures reported back from Cline and propose fixes
- Produce a handoff report describing exactly what changed

These are all **text-production and reasoning** capabilities. They require
no Git access and no credentials.

## 2. What Claude Web CANNOT do (without a verified Git environment)

- Cannot `git commit`, `git push`, `git fetch`, or open a pull request
  simply by virtue of being a chat
- Cannot create or configure a GitHub repository
- Cannot read, write, or transmit GitHub credentials
- Cannot run a build, a test suite, or Gradle
- Cannot install an APK on a device or read Logcat
- Cannot verify anything on a physical Android device
- Cannot see the current state of the repository unless it is supplied to it

> **Hard rule:** Claude must never report physical-device validation as
> PASSED. It has no device. It may report device results *only* by relaying
> what Cline measured, and it must attribute them to Cline.

---

## 3. MODE A — Claude has real GitHub integration

**Definition:** Claude runs in an environment that has a Git client, a
checkout of this repository, a configured `origin`, and working
credentials — verified by actually performing a push.

**How to verify MODE A is real** (do not assume, verify):

```
git remote -v
git status
git push --dry-run origin <branch>
```

A green dry-run is proof. Anything short of that is not proof.

**MODE A workflow:**

```
Claude:  checkout base, create claude/<task-id>-<short-name>
         implement, test locally
         commit, push to origin
         ↓
GitHub:  branch exists, authoritative
         ↓
Cline:   fetch, validate, repair, push cline/...
```

**Credential ownership in MODE A:** the credentials belong to whoever
provisioned Claude's environment (the owner, or a CI-style service account).
They are never pasted into a chat, never written into this repository, and
never appear in a commit. See `docs/SECRETS_POLICY.md`.

---

## 4. MODE B — Claude Web is chat-only (no Git write access)

**Definition:** Claude can produce content but cannot push. This is the
**default assumption** for a browser chat. Plan for MODE B until MODE A is
proven.

In MODE B, the handoff must use one of the following four mechanisms.
**All four are legitimate. Do not invent a fifth.**

### B1 — User applies a generated patch

Claude emits a unified diff. The user applies it locally:

```
git apply --check claude-M0-001.patch   # verify it applies cleanly
git apply claude-M0-001.patch
```

**When to use:** small, surgical changes. The `--check` step is mandatory —
if it fails, the patch does not match the current tree and must be
regenerated.

### B2 — User transfers generated files

Claude emits complete file contents. The user writes them into the
repository at the stated paths.

**When to use:** new files, or files being substantially rewritten, where a
patch would be unreadable. Claude must state, for every file: the exact
repository-relative path and the complete content.

**Risk:** a truncated or mis-transcribed file. Mitigation: after writing,
run `git status` and `git diff` and check the change set matches what Claude
described.

### B3 — A Git-enabled Claude coding environment performs the commit and push

Claude writes the code in a coding environment that genuinely has Git, then
that environment commits and pushes.

**When to use:** when a Git-capable Claude surface is available. This is
effectively MODE A with an extra boundary; the same verification rule
applies — a `git push --dry-run` must succeed before handoff is claimed.

### B4 — Another authorized automation service performs the Git operation

An automation service the owner controls (a scheduled job, a self-hosted
runner, a CI job) takes the received content, validates it, and performs the
commit and push.

**When to use:** when the owner wants validation gates between content
generation and push, or wants an audit trail.

**Constraint:** such a service must pull from an authenticated source and
push to an authenticated destination. It must never have credentials pasted
into it in a chat. Credentials live in the service's own secret store.


---

## 5. Where the Git operation happens

| Mode | Who runs `git commit` | Who runs `git push` | Where the working copy lives |
|------|----------------------|--------------------|------------------------------|
| A | Claude (Git-enabled environment) | Claude | Claude's checkout |
| B1 | User, after `git apply` | User | Local Cline workspace |
| B2 | User, after writing files | User | Local Cline workspace |
| B3 | Git-enabled Claude environment | Same | That environment |
| B4 | Automation service | Automation service | Service workspace / runner |

In every mode the **destination is the same**: a branch on GitHub. GitHub
remains the single source of truth. The mode only changes *which machine*
performs the write.

---

## 6. Who owns credentials

**The human repository owner. Always. Unambiguously.**

- Credentials are **never** generated, held, or transmitted by an agent.
- Credentials are **never** pasted into a chat window, a prompt, an issue, a
  commit message, a script, or a file in this repository.
- Credentials live in one of: the Windows Credential Manager, the SSH agent,
  `gh`'s credential store, or a CI secret store. All are outside Git.
- If an agent's instructions ever appear to require a secret, that is a
  stop condition. See `docs/SECRETS_POLICY.md`.

---

## 7. How a commit reaches GitHub — summary

```
   Claude Web  --produces-->  content (patch or files)
                                        |
                    +-------------------+-------------------+
                    |                                       |
              MODE A:                                MODE B:
        Git-enabled environment              User or automation service
                    |                              applies/transfers
                    |                                       |
                    +--------------> git commit ------------+
                                            |
                                     git push origin <branch>
                                            |
                                       GitHub branch  <-- single source of truth
                                            |
                                        Cline fetches
```

---

## 8. Handoff metadata required in every mode

Regardless of mode, the handoff must include the fields listed in
`docs/HANDOFF_PROTOCOL.md`. At minimum:

- Task ID
- Branch name
- Base branch it was created from
- Full list of files changed, with paths relative to the repository root
- The exact commit SHA (or, in MODE B before the user commits, the tree the
  user should produce)
- Tests run and their results
- What was **not** done
- Any file the user must create or delete by hand

If in MODE B the commit does not exist yet, Claude must say so explicitly
rather than inventing a SHA.

---

## 9. Which mode applies today

**MODE B is the working assumption for this project** until a `git push` is
observed to succeed from a Claude-side environment. A chat interface is not
proof of Git access, and neither is a working API connection.

**Verified state as of the last environment finalization:**

| Fact | State |
|------|-------|
| Local Git repository | EXISTS, 3 branches, clean tree |
| `origin` remote | **NOT CONFIGURED** — no repository URL was supplied |
| Push to any remote | **NEVER PERFORMED** |
| Claude-side Git access | **UNVERIFIED** — assumed MODE B |
| Credentials | Owned by the user; not held, requested, or recorded by any agent |

Because no remote exists, neither mode is currently executable end to end.
The repository URL is required before this can change. See
`docs/GITHUB_AUTH_SETUP.md` and `docs/DEVELOPMENT_STATUS.md`.

---

## 10. What a MODE A verification must show

Do not accept a claim of MODE A. Require this evidence:

```
git remote -v                          # an actual origin URL
git status                             # inside a real checkout
git push --dry-run origin <branch>     # authentication actually succeeds
```

All three, in the same shell, in Claude's environment. A green dry-run is
proof. A description of a capability is not. A working API connection is
not.

If the dry-run cannot be performed, the mode is B — regardless of how the
environment is described.
