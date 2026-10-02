# Hermes Android — Development Environment

This repository currently contains **development infrastructure only**.
It is the prepared working environment for a future Android AI Agent
project. **No product code has been written here, and none should be
until the architecture audit task is started deliberately.**

---

## What this repository is

A two-agent development workflow with GitHub as the single source of truth:

- **Claude Web** — architect and primary builder
- **Cline Desktop** — integration, build, test, device validation, repair
- **GitHub** — the canonical record
- **A physical Android phone** — the only accepted real-world test target

The loop is documented in [`docs/DEVELOPMENT_LOOP.md`](docs/DEVELOPMENT_LOOP.md).

## What this repository is NOT

It is not the Hermes Android Harness, not a bridge, not an Android agent,
and not any product feature. Implementing those is a later task, and
starting them without the audit would violate the workflow.

---

## Start here

| If you want to know... | Read |
|------------------------|------|
| What is true right now | [`DEVELOPMENT_STATUS.md`](DEVELOPMENT_STATUS.md) |
| Who does what | [`docs/AGENT_ROLES.md`](docs/AGENT_ROLES.md) |
| How the loop works | [`docs/DEVELOPMENT_LOOP.md`](docs/DEVELOPMENT_LOOP.md) |
| Whether the machine is ready | [`docs/DEVELOPMENT_ENVIRONMENT_BASELINE.md`](docs/DEVELOPMENT_ENVIRONMENT_BASELINE.md) |
| How to set up GitHub access | [`docs/GITHUB_AUTH_SETUP.md`](docs/GITHUB_AUTH_SETUP.md) |
| Whether Claude can actually push | [`docs/CLAUDE_GITHUB_ACCESS.md`](docs/CLAUDE_GITHUB_ACCESS.md) |
| How to use the phone | [`docs/ANDROID_DEVICE_WORKFLOW.md`](docs/ANDROID_DEVICE_WORKFLOW.md) |

---

## Branch model

```
main                       stable, protected, never worked on directly
develop                    integration branch
claude/<task-id>-<name>    Claude's implementation branches
cline/<task-id>-<name>     Cline's validation/repair branches
```

Task IDs: `M<milestone>-<sequence>`, e.g. `M0-001`, `M1-002`.
Full model: [`docs/HANDOFF_PROTOCOL.md`](docs/HANDOFF_PROTOCOL.md).

---

## Quick commands

```powershell
# Where am I, and is the tree clean?
.\scripts\git-status.ps1

# Is the machine ready?
.\scripts\doctor.ps1

# Pull in Claude's work
.\scripts\git-fetch-all.ps1
.\scripts\git-check-branch.ps1 -Branch claude/M0-001-audit

# Build and test
.\scripts\build.ps1
.\scripts\test.ps1

# Physical device loop (once an app ID exists)
adb devices
.\scripts\full-device-test.ps1 -PackageName <app-id>

# Branch and push
.\scripts\git-create-branch.ps1 -Agent cline -TaskId M0-001 -ShortName fix-x -Base claude/M0-001-audit
.\scripts\git-push-branch.ps1 -Branch cline/M0-001-fix-x
```

Every script is project-relative, contains no secrets, and hardcodes no
username or personal path.

## Documentation index

Every document is reachable from here. Nothing important is buried.

### Specification and state

| Document | What it is |
|----------|------------|
| [`Hermes_Android_Agent_PROJECT_PLAN.md`](Hermes_Android_Agent_PROJECT_PLAN.md) | **The master specification.** Authoritative for the product. Not rewritten by environment work. |
| [`DEVELOPMENT_STATUS.md`](DEVELOPMENT_STATUS.md) | Authoritative **product/task** state. Both agents update it. |
| [`docs/DEVELOPMENT_STATUS.md`](docs/DEVELOPMENT_STATUS.md) | **Environment** status scan (this finalization's measurements). |

### Roles and workflow

| Document | What it defines |
|----------|-----------------|
| [`docs/AGENT_WORKFLOW.md`](docs/AGENT_WORKFLOW.md) | **Start here.** Claude, GitHub, Cline, the phone, and the user. |
| [`docs/AGENT_ROLES.md`](docs/AGENT_ROLES.md) | Detailed MAY / MAY NOT per agent |
| [`docs/DEVELOPMENT_LOOP.md`](docs/DEVELOPMENT_LOOP.md) | The canonical loop, stage by stage |
| [`docs/CLAUDE_WORKFLOW.md`](docs/CLAUDE_WORKFLOW.md) | Claude's 12-step procedure |
| [`docs/CLINE_WORKFLOW.md`](docs/CLINE_WORKFLOW.md) | Cline's 21-step procedure |
| [`docs/CLAUDE_TASK_PROTOCOL.md`](docs/CLAUDE_TASK_PROTOCOL.md) | Claude's task lifecycle |
| [`docs/CLINE_TASK_PROTOCOL.md`](docs/CLINE_TASK_PROTOCOL.md) | Cline's task lifecycle |

### Handoff

| Document | What it defines |
|----------|-----------------|
| [`docs/HANDOFF_PROTOCOL.md`](docs/HANDOFF_PROTOCOL.md) | Both handoff directions, required fields, task IDs |
| [`docs/REPORT_FORMAT.md`](docs/REPORT_FORMAT.md) | The exact report template |
| [`handoff/README.md`](handoff/README.md) | Handoff directory layout and templates |

### GitHub access

| Document | What it defines |
|----------|-----------------|
| [`docs/CLAUDE_GITHUB_ACCESS.md`](docs/CLAUDE_GITHUB_ACCESS.md) | MODE A / MODE B. **Clean APIs is not GitHub access.** |
| [`docs/GITHUB_AUTH_SETUP.md`](docs/GITHUB_AUTH_SETUP.md) | Auth options and the owner checklist |

### Android and device

| Document | What it defines |
|----------|-----------------|
| [`docs/ANDROID_DEVICE_WORKFLOW.md`](docs/ANDROID_DEVICE_WORKFLOW.md) | USB-C → ADB → install → launch → Logcat |
| [`docs/DEVELOPMENT_ENVIRONMENT_BASELINE.md`](docs/DEVELOPMENT_ENVIRONMENT_BASELINE.md) | Full machine inventory |
| [`.github/workflows/README.md`](.github/workflows/README.md) | CI stages and the enablement checklist |

### Policy

| Document | What it defines |
|----------|-----------------|
| [`docs/SECRETS_POLICY.md`](docs/SECRETS_POLICY.md) | What must never enter the repository |

---

## Build and test instructions

There is **no build yet** — the Android product project does not exist. When
it does, these are the commands, unchanged:

```powershell
.\scripts\build.ps1            # Gradle assembleDebug  -> APK
.\scripts\test.ps1             # lint + unit tests
```

Until then both report `NOT CONFIGURED` (exit 3). That is intentional and is
not a failure.

---

## Environment instructions

```powershell
.\scripts\doctor.ps1           # everything: git, tools, SDK, device
.\scripts\android-check.ps1    # JDK, Gradle, SDK, adb only
.\scripts\device-check.ps1     # the physical phone only
```

---

## Current blockers

| ID | Blocker | Owner action |
|----|---------|--------------|
| B1 | Target repository does not exist | Create `hermes-android-harness` (Public) at https://github.com/new — **do not** tick "Add a README" |
| B2 | Token cannot create repositories (403) | Use the web UI, or issue a token with repo-creation rights |
| — | No GitHub CLI (`gh`) | Optional: `winget install --id GitHub.cli` |

**Already resolved:** git identity is set to `quyenbe0402`, GitHub network
access works, and **push authentication is verified** (proven with a dry run
that transferred nothing).

A physical Android device is connected and ready (see
`docs/ANDROID_DEVICE_WORKFLOW.md`).

Once the repository exists, connect it:

```
git remote add origin https://github.com/quyenbe0402/hermes-android-harness.git
```

Full measured status: [`docs/DEVELOPMENT_STATUS.md`](docs/DEVELOPMENT_STATUS.md).

---

## Directory layout

```
docs/            all workflow documentation (start at AGENT_WORKFLOW.md)
scripts/         PowerShell tooling for git, build, test, device
handoff/         agent-to-agent transport area (claude/ cline/ reports/)
.github/         CI skeleton (ci.yml) - product jobs gated on Gradle
DEVELOPMENT_STATUS.md    live product/task state - read this first
Hermes_Android_Agent_PROJECT_PLAN.md    the master specification
```
