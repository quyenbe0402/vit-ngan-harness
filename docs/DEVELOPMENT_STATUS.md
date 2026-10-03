# Development Status (Environment)

**Scanned:** 2026-10-02 · **By:** Cline Desktop (environment finalization)

> Every status below is **measured**, not assumed. Anything that could not be
> verified says so explicitly.
>
> The authoritative product/task state is `DEVELOPMENT_STATUS.md` at the
> repository root. This file covers the **development platform**.

---

## Summary

| Area | Status | Detail |
|------|--------|--------|
| Git repository | **PASS** | 3 branches, clean tree |
| Git identity | **PASS** | `quyenbe0402 <quyenbe0402@gmail.com>`, repo-local |
| GitHub network | **PASS** | `git ls-remote` reaches GitHub |
| GitHub account | **PASS** | `quyenbe0402` confirmed via API |
| Push authentication | **PASS** | verified by dry run, nothing transferred |
| Git remote `origin` | **PASS** | `vit-ngan-harness` live, fetch+push working |
| Push to `origin` | **PASS** | `main` and `develop` pushed, tracking set |
| Branch model | **PASS** | main / develop / claude/* / cline/* |
| PowerShell scripts | **PASS** | 18 scripts, all parse, guards verified |
| Android SDK | **PASS** | platforms 36/36.1/37.0, build-tools 36.0.0 |
| ADB | **PASS** | 1.0.41 (37.0.1-15733141) |
| Physical device | **PASS** | connected and authorised, properties read |
| Device validation | **NOT TESTED** | no APK exists to install |
| CI skeleton | **PASS** | `ci.yml` valid, 3 jobs, gated on Gradle |
| Handoff structure | **PASS** | claude/ cline/ reports/ + README |
| Secret protection | **PASS** | no credentials tracked, scan passes |
| Product implementation | **NONE** | correct — none performed |

---

## 1. Environment status

Windows 10.0.26200 (build 26100) · PowerShell 5.1 Desktop · user-level
`ANDROID_HOME` and `JAVA_HOME` both set. The machine is adequate for Android
development. Full detail: `docs/DEVELOPMENT_ENVIRONMENT_BASELINE.md`.

## 2. Git status

| Field | Value |
|-------|-------|
| Git | 2.55.0.windows.3 |
| Branch | `develop` |
| Working tree | **CLEAN** |
| History | 4 commits, linear with merges |
| Tracked files | 39 + this finalization's additions |

## 3. GitHub status — **CONNECTED AND PUSHING**

| Field | Value |
|-------|-------|
| `origin` (fetch + push) | `https://github.com/quyenbe0402/vit-ngan-harness.git` |
| Remote repository | **live**, public |
| Branches pushed | `main` → `origin/main`, `develop` → `origin/develop` |
| Tracking configured | ✅ both branches |
| `git fetch origin` | exit 0 |
| Working tree | clean, up to date with `origin/develop` |
| Account | `quyenbe0402` |
| Token | fine-grained, scoped to `vit-ngan-harness` |

### 3.1 Verified pushed state

```
$ git ls-remote --heads origin
69fcb4600b9ffac7ec560e526089f68c692070e1  refs/heads/develop
cbecfa3dc349c7654feab4239ea431c3b6fd2ae4  refs/heads/main

$ git branch -vv
  develop  69fcb46 [origin/develop] chore(M0-002): connect origin...
  main     cbecfa3 [origin/main]    merge(M0-001): develop into main
```

Remote `main` contains all 9 top-level entries: `.gitattributes`,
`.github`, `.gitignore`, `DEVELOPMENT_STATUS.md`,
`Hermes_Android_Agent_PROJECT_PLAN.md`, `README.md`, `docs`, `handoff`,
`scripts`. SHA values match the local branches exactly.

### 3.2 Two token permissions were required

Getting this working took two permission additions, both discovered by
failing and measuring rather than guessing:

| Permission | Why | Symptom when missing |
|------------|-----|----------------------|
| `Contents: Read and write` | Push repository content | `403 Permission denied` |
| `Workflows: Read and write` | Push `.github/workflows/*` | `refusing to allow a PAT to create or update workflow ... without workflow scope` |

The second is easy to miss: GitHub treats files under `.github/workflows/`
as **executable code** and gates them behind a separate scope from
`Contents`. A token with full `Contents` access still cannot push CI files.

### 3.3 Credential handling note

The token is supplied via the shell environment and passed to git per
invocation. It is **never** written to:

- `.git/config` (verified — `git remote -v` shows the clean HTTPS URL)
- any tracked file
- any script in this repository

Pushes use `git -c credential.helper=` so the machine's default credential
helper is bypassed for those commands, avoiding accidental caching.

**Standing recommendation:** this token was pasted into a chat session, so
treat it as exposed and rotate it when convenient. The workflow works with
any correctly-scoped token, and equally with an SSH key — no code change is
needed to switch.


## 4. Branch status

| Branch | Role | Present |
|--------|------|---------|
| `main` | stable | yes |
| `develop` | integration | yes |
| `cline/M0-001-fix-scripts` | prior repair | yes (retained, not deleted) |
| `claude/<task-id>-<name>` | Claude implementation | pattern defined, none active |
| `cline/<task-id>-<name>` | Cline repair | pattern defined |

**Enforced in code** by `scripts/branch-push.ps1`, which refuses to push to
`main` or `develop`, has no force option, and runs no destructive command.
Verified by execution: all three refusals returned exit 2.


## 5. Cline status

| Field | Value |
|-------|-------|
| Workspace | this repository, clean tree |
| Tooling | 18 PowerShell scripts under `scripts/` |
| Can fetch | **NO** — no remote |
| Can build | **NOT CONFIGURED** — no Gradle project |
| Can reach the device | **YES** |
| Can push | **NO** — no remote |

Cline is ready for everything that does not require a remote or a build.

## 6. Android SDK status

| Component | Value | State |
|-----------|-------|-------|
| SDK root | `%LOCALAPPDATA%\Android\Sdk` | PASS |
| platform-tools | present | PASS |
| build-tools | 36.0.0 | PASS |
| platforms | android-36, android-36.1, android-37.0 | PASS |
| JDK | OpenJDK 25.0.2 (Android Studio JBR) | PASS, not on PATH |
| Gradle | none standalone, no wrapper | WARN — not a blocker |
| Android Studio | 261.26222.65.0-AI | PASS |

No SDK version was changed. Verify with `.\scripts\android-check.ps1`
(exit 0, 0 FAIL).

> **Note for the project owner:** JDK 25 is newer than some Android Gradle
> Plugin versions support. If a build later rejects it, install JDK 17 LTS and
> repoint `JAVA_HOME`. Deliberately not changed now, because no project exists
> to build and guessing a change could break the working setup.

## 7. ADB status

| Field | Value |
|-------|-------|
| Version | 1.0.41 (Version 37.0.1-15733141) |
| Server | running, port 5037 |
| Devices | 1 authorised |
| State | **PASS** |

## 8. Physical device status

| Property | Value |
|----------|-------|
| Serial | `b36d068a` |
| Manufacturer / brand | Xiaomi / Redmi |
| Model | `24069RA21C` |
| Android | 16 |
| API level | 36 |
| ABI | `arm64-v8a` (only) |
| ADB state | `device` (authorised) |
| Connection | USB-C |

**Connectivity: PASS. Device validation: NOT TESTED** — there is no
application to install. These are separate claims.

Two consequences for future work: API 36 is a recent platform with stricter
background and permission rules; `arm64-v8a`-only means a native library
built for another ABI will fail here but not on an x86_64 emulator.

## 9. Scripts status

18 scripts. All parse cleanly under the PowerShell AST parser. No secrets, no
hardcoded usernames, no personal absolute paths.

| Group | Scripts |
|-------|---------|
| Diagnostics | `doctor.ps1`, `android-check.ps1`, `device-check.ps1` |
| Git | `git-status.ps1`, `git-sync.ps1`, `git-fetch-all.ps1`, `git-check-branch.ps1`, `git-create-branch.ps1`, `git-push-branch.ps1`, `branch-push.ps1` |
| Build/test | `build.ps1`, `test.ps1` |
| Device | `install-device.ps1`, `launch-device.ps1`, `device-logcat.ps1`, `collect-logcat.ps1`, `full-device-test.ps1` |
| Shared | `lib/Common.ps1` |

`git-sync.ps1` and `test.ps1` have never been executed successfully — the
first needs a remote, the second a Gradle project. Both parse.

## 10. Documentation status

21 documents: 16 in `docs/`, `README.md`, `DEVELOPMENT_STATUS.md`,
`handoff/README.md`, `.github/workflows/README.md`, plus the master
specification. Discoverable from the repository root via `README.md`.

`docs/AGENT_WORKFLOW.md` (roles, including the user as final authority) and
`handoff/README.md` are new in this finalization. No existing document was
rewritten; `CLAUDE_GITHUB_ACCESS.md` was extended, not replaced.

## 12. Blockers

| # | Blocker | Owner action | Severity |
|---|---------|--------------|----------|
| 1 | `gh` not installed | `winget install --id GitHub.cli` | low — PRs via web UI |
| 2 | Token was exposed in chat | Rotate it when convenient | low — recommend, not blocking |
| 3 | `main` / `develop` diverged | Resolves on the next `develop` → `main` merge | low — informational |
| 4 | No Gradle project | Comes with M0 product work | expected, not a defect |
| 5 | Credential-helper override depends on `$GITHUB_TOKEN` | Works today; decide whether to keep | low |

| Clean APIs mode | **UNKNOWN** | awaiting one Playground test — see `docs/CLEAN_APIS_MODE_DECISION.md` |

**No hard blockers remain.** GitHub is the source of truth and the
Claude → GitHub → Cline handoff chain is executable end to end for the
first time.

## 15. Clean APIs access mode — the one open question

| Field | Value |
|-------|-------|
| Account | `quyenbe0402`, authenticated |
| Repository | `quyenbe0402/vit-ngan-harness` |
| Claude-side shell/filesystem | **UNVERIFIED** |
| Working assumption | **MODE B** (chat-only, no repository write) |
| Decision procedure | `docs/CLEAN_APIS_MODE_DECISION.md` |

Clean APIs is an API/model gateway. A working model connection is **not**
evidence of GitHub write access — the two are separate facts, and there is
no "link GitHub to Clean APIs" step.

Until an actual push is observed from Claude's environment, MODE B applies:
Claude produces a patch or file set, the owner applies and commits, and
Cline validates. Both procedures are pre-written so no time is lost once
the answer is known.

## 13. Git identity — CORRECTED

The previous environment setup had inserted a **fabricated** identity
(`Cline Desktop <cline@localhost>`). It has been replaced with the real one
supplied by the owner.

| Scope | Name | Email |
|-------|------|-------|
| **Local (`.git/config`)** | **`quyenbe0402`** | **`quyenbe0402@gmail.com`** |
| Global | *(unset)* | *(unset)* — deliberately left alone |

The correction was applied **locally only**, exactly as instructed. The
global configuration was not modified and remains unset, so this change
affects only this repository.

### One residual issue, not auto-fixed

The 5 commits authored before this correction carry the fabricated author.
They are already on GitHub in the future and, more importantly, rewriting
published history is the **owner's decision**, not an agent's. It is also
unnecessary: the 5 commits are development-infrastructure setup commits
containing no secrets and no product code.

**If you want them corrected**, the options are:

- *Safest, recommended:* leave them. A wrong author on setup commits is
  cosmetic, and the correct identity applies from the next commit onward.
- *Rewrite before anything is pushed elsewhere* (only safe while the history
  exists on exactly one machine):

```
git rebase --root --exec 'git commit --amend --reset-author --no-edit'
```

This rewrites every commit SHA. **Do not run it** once other machines or
branches have the history.

---

## 14. Next required action

**The environment is ready. There is no blocking action left.**

Optional cleanups, none blocking:

| Action | Why |
|--------|-----|
| Rotate the GitHub token | It was pasted into a chat session |
| `winget install --id GitHub.cli` | Enables `gh pr create` from the CLI |
| Merge `develop` into `main` | Settles the branch divergence |

**Next product step — not started, by instruction:**

> M0 architecture audit of the Hermes Android project

This task was environment finalization only. No product code was written,
no Hermes source was modified, and M0 product work has not begun.


## 11. Secret protection status

| Check | Result |
|-------|--------|
| Credentials in tracked files | **none found** |
| `.gitignore` coverage | adequate |
| CI literal-secret scan | passes |
| CI tracked-secret scan | passes |
| Tokens written to docs | **none** |
| `.env` files created | **none** |

**Carried-forward risk (not fixed, user decision):** global `.gitconfig`
overrides `credential.helper` with a function that reads `$GITHUB_TOKEN`.
This disables Git Credential Manager and makes every push depend on an
out-of-band environment variable. Not a repository leak, but a reliability
and blast-radius risk. See `docs/GITHUB_AUTH_SETUP.md` §4.

## M0-006 Hermes bridge foundation

**Branch**: `cline/M0-006-hermes-bridge` (from develop @ `8e32a58`)
**Status**: complete, on branch - not yet merged to develop

### Upstream
NousResearch/hermes-agent @ `eaecc99c7ec5b6f37e880a0b69d16871cd3e4f57`.
The project's "v0.20.5" reference is wrong; upstream tags are date-based.

### Delivered
- `HermesTransport` + newline-framed `HermesFraming` (upstream `serialize_frame`)
- `HermesProtocolAdapter`: encode/decode, typed results, CoT filtering
- `HermesBridge`: correlation, timeouts, cancellation, server requests, replay
- `HermesBridgeError`: 9 typed failures, upstream code/message preserved
- `HermesSessionRef`: the five-id mapping, incl. the live-vs-stored trap
- `FakeHermesTransport` for tests
- `BridgeBackedHermesRuntime`: adapts the bridge to the M0-004 contract

### Validation
- 299 unit tests, 0 failures, 2 skipped (120 new for M0-006)
- 12 instrumented tests on 24069RA21C / Android 16
- assembleDebug green
- Static scan: no process exec, no filesystem, no network, no new permissions

### Not done (by design)
No Termux, no embedded Python, no Chaquopy, no Node, no Hermes on device,
no MCP, no plugins. Hermes does not run yet and is not claimed to.

### GPT review
GPT_ADVISOR_UNAVAILABLE - no second model is callable in this environment.