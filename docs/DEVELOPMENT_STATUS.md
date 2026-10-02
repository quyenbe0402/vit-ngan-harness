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
| Git remote `origin` | **PASS** | `vit-ngan-harness` connected, `ls-remote` exit 0 |
| Push to `origin` | **BLOCKED** | 403 — token not scoped to this repo |
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

## 3. GitHub status — **REMOTE CONNECTED, PUSH BLOCKED**

| Field | Value |
|-------|-------|
| `origin` (fetch) | `https://github.com/quyenbe0402/vit-ngan-harness.git` |
| `origin` (push) | `https://github.com/quyenbe0402/vit-ngan-harness.git` |
| Remote repository | **EXISTS** — public, 0 KB, default branch `main` |
| `git ls-remote origin` | exit 0, **0 refs** (repo is empty) |
| Network reachability | **PASS** |
| Account | `quyenbe0402` — confirmed via API |
| `GITHUB_TOKEN` | present, authenticates as `quyenbe0402` |
| Token type | **fine-grained** |
| **Push to `origin`** | **403 DENIED** — see §3.2 |
| Push to `game-ngoc-rong-offline` | works (proves the credential path itself is valid) |

### 3.1 What is done

`origin` is configured and reachable. `git ls-remote origin` succeeds and
correctly reports the remote as empty, so the first push will establish
`main` as the default branch with no conflict.

### 3.2 Why the push is denied — fine-grained token scope

```
remote: Permission to quyenbe0402/vit-ngan-harness.git denied to quyenbe0402.
fatal: ... The requested URL returned error: 403
```

This is **not** a network, URL, or branch problem. The evidence:

| Test | Result |
|------|--------|
| `git ls-remote origin` | exit 0 — network and URL are correct |
| API read of `vit-ngan-harness` | OK, `permissions.push = true` |
| Dry-run push to `vit-ngan-harness` | **403** |
| Dry-run push to `game-ngoc-rong-offline` | **exit 0 — succeeds** |

The same token pushes successfully to the older repository but is refused
by the new one. That isolates the cause to the token's **repository
selection**: the fine-grained token was granted access to
`game-ngoc-rong-offline` only, and `vit-ngan-harness` was never added to it.

The API's `permissions.push = true` describes the *account's* rights on the
repository, not what this particular token is scoped to. Those are different
things, which is why the API looked permissive while the push was refused.

### 3.3 Owner action — fix the token scope

1. Open https://github.com/settings/personal-access-tokens
2. Edit the token currently exported as `GITHUB_TOKEN`
3. Under **Repository access**, add `vit-ngan-harness`
4. Confirm **Permissions → Repository contents: Read and write**
5. Save — the token value stays the same, so `$env:GITHUB_TOKEN` does not
   need to be replaced unless the token was regenerated

Then re-run, in the same PowerShell session:

```powershell
.\scripts\git-fetch-all.ps1
git push -u origin main
git push -u origin develop
git branch -vv
```

If the token value was regenerated, re-export it before pushing:

```powershell
$env:GITHUB_TOKEN = '<new value>'
```

**Never commit the token and never paste it into a file in this repository.**

### 3.4 Note on the branch divergence

`main` and `develop` have diverged: `develop` is **2 commits ahead**,
`main` is **1 commit ahead** (a merge commit from the earlier setup). This
is normal after the setup merges and resolves itself once both are pushed —
GitHub will simply show both branches. A future merge of `develop` into
`main` will settle it.


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
| 1 | Token not scoped to `vit-ngan-harness` (403) | Edit the fine-grained token, add this repo, contents: read+write | **critical** — the loop stops here |
| 2 | `main` / `develop` diverged | Resolves on its own once both are pushed | low — informational |
| 3 | 5 commits carry the old fabricated author | Optional; see §13. Recommended to leave. | low |
| 4 | No Gradle project | Comes with M0 product work | expected, not a defect |
| 5 | `gh` not installed | `winget install --id GitHub.cli` | low — PRs via web UI |
| 6 | Credential-helper override depends on `$GITHUB_TOKEN` | Works today; decide whether to keep | low — currently functional |

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

**One action, and it blocks everything downstream:**

> Open https://github.com/settings/personal-access-tokens, edit the token
> exported as `GITHUB_TOKEN`, and add `vit-ngan-harness` to its repository
> access with **Repository contents: Read and write**.

Then, in the same PowerShell session:

```powershell
.\scripts\git-fetch-all.ps1
git push -u origin main
git push -u origin develop
git branch -vv     # expect: main -> origin/main, develop -> origin/develop
git status
```

`origin` is already configured and reachable, so no other setup is needed —
this is the last blocker.

Product work (M0) has **not** started, and is not started by this task.


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
