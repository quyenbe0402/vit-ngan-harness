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

---

## Current blockers

| ID | Blocker | Owner action |
|----|---------|--------------|
| B1 | No Git remote configured | Create the GitHub repo, then `git remote add origin <url>` |
| B2 | No Git commit identity | `git config user.name` / `user.email` |
| B3 | No physical Android device connected | Connect by USB-C, enable USB debugging, accept the ADB prompt |
| B4 | GitHub authentication unverified | Follow `docs/GITHUB_AUTH_SETUP.md` |

---

## Directory layout

```
docs/            all workflow documentation (start at AGENT_ROLES.md)
scripts/         PowerShell tooling for git, build, test, device
handoff/         agent-to-agent transport area (never secrets)
.github/         future CI structure (no workflows enabled yet)
DEVELOPMENT_STATUS.md    live shared state — read this first
```
