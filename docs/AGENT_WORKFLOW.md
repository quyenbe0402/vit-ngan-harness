# Agent Workflow

Who does what, and where the boundaries are. This is the single reference for
the roles in the Hermes Android Harness development loop.

For the loop itself see `docs/DEVELOPMENT_LOOP.md`. For what each agent may
and may not do see `docs/AGENT_ROLES.md`.

```
  Claude Web / Clean APIs
        |  reads master spec, plans, implements
        v
      GitHub            <-- source of truth, handoff point
        |  branch pushed
        v
  Cline Desktop
        |  fetch, build, test
        v
  Physical Android phone (USB-C / ADB)
        |  install, launch, logcat, test
        v
  Cline repair branch -> push -> GitHub
        |
        v
  Claude reads the validation result -> next task
```

---

## 1. Claude Web / Clean APIs

**Role: primary architect + primary implementation agent**

### Is

- The reader of the master specification (`MASTER_SPEC.md` /
  `Hermes_Android_Agent_PROJECT_PLAN.md`)
- The author of implementation plans
- The writer of product source code, unit tests and developer documentation
- The creator of implementation commits on `claude/<task-id>-<short-name>`
- The preparer of the handoff document for Cline

### Does

- Translate the specification into concrete, task-scoped changes
- Own architecture decisions, and record them in the handoff
- Write tests that will fail if the behaviour regresses
- Review Cline's validation report and decide the next task

### Does NOT

- Run a physical device. It has none
- Claim device validation passed, under any circumstances
- Push to `main` or `develop`
- Touch credentials

### On "Clean APIs"

**Clean APIs is a model/API transport layer.** It means the environment can
reach a model. It is **not** evidence that the environment can reach GitHub.

A working API connection proves nothing about repository write access. The
access model is specified in `docs/CLAUDE_GITHUB_ACCESS.md`, and the default
assumption is MODE B (no direct write) until a push is observed to succeed.

---

## 2. GitHub

**Role: source of truth**

### Is

- The canonical repository
- The durable record of branches, commits and merged history
- **The implementation handoff point** — where Claude's work becomes real
- **The review and merge point** — where work becomes trusted
- The place task state lives between sessions

### Does NOT

- Hold secrets
- Get bypassed. If it is not on GitHub, it was not handed off

Not the source of truth: chat history, local uncommitted files, temporary
files, or a branch that exists only on one laptop.

---

## 3. Cline Desktop

**Role: integration + build + test + device validation + repair**

Cline is the agent that **measures**. It runs things and reports what
happened.

### Is

- The **integration agent** — merges handoffs into the working tree
- The **build agent** — runs Gradle
- The **test agent** — runs unit and integration tests
- The **physical-device validation agent** — installs and exercises the app
  on the real phone
- The **repair agent** — fixes implementation-level defects
- The **log analysis agent** — reads Logcat and finds the real cause
- The **regression validation agent** — re-runs everything after a repair

### Does

- Fetch, inspect, build, test, install, launch, capture, diagnose, repair,
  retest, report — the full cycle in `docs/CLINE_WORKFLOW.md`
- Work on `cline/<task-id>-<short-name>` for repairs
- Commit and push its own branch
- Report BLOCKED honestly when a prerequisite is missing

### Does NOT

- Develop on `main` or merge into `main` (enforced in `scripts/branch-push.ps1`)
- Force push (never; the script has no force option at all)
- Hard-reset a shared branch
- Change architecture — it reports architectural problems, Claude fixes them
- Expand scope: a repair fixes what is broken, it does not add features
- Claim a test passed that was not run, or a device result not observed


---

## 4. Physical Android phone

**Role: real-world validation target**

### Is

- Connected over **USB-C**, accessed through **ADB**
- The only accepted evidence of real runtime behaviour
- The place where permission flows, lifecycle, background limits, OEM
  restrictions and real performance are exposed

### Does NOT

- Get replaced by an emulator for validation. An emulator PASS is not a
  device PASS
- Get assumed. If no device is connected, the state is `NOT_CONNECTED` and
  validation is `NOT TESTED` — never `PASS`

Current device: see `docs/DEVELOPMENT_STATUS.md`.

---

## 5. The User

**Role: final authority**

The user is the decision-maker, and the only holder of credentials. Neither
agent can substitute for this role.

### Decides

- **Repository decisions** — creating the remote, branch protection, merge
  policy, deleting branches, rewriting history
- **Distribution decisions** — signing keys, release builds, store or sideload
- **Credentials** — who authenticates, with what, and where it is stored
- **Dangerous capabilities** — accessibility services, MediaProjection,
  device-management privileges, background execution

### Owns

- The GitHub account and all credentials
- The signing keystore (never committed, never shared with an agent)
- The decision to enable any privileged Android capability

### Why this role exists

Both agents are automated. Neither can be permitted to grant itself a
dangerous capability, and neither may be handed a credential. Where the
workflow says "blocked", the unblocking action belongs here.

---

## 6. Decision table

| Question | Decided by |
|----------|-----------|
| What to build next | Claude, from the specification |
| How it is structured | Claude |
| Task scope | Claude, from the specification |
| Does it build? | **Cline** (measured) |
| Do tests pass? | **Cline** (measured) |
| Does it work on a phone? | **Cline** (measured) |
| Is a repair in scope? | Cline, escalating to Claude if not |
| Is the architecture right? | Claude |
| Repository / distribution / credentials / dangerous capabilities | **The user** |
| What is true right now | **GitHub** |
