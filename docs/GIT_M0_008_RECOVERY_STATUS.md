# M0-008 Git Recovery Status

Generated 2026-10-03. Verification only: no build, no runtime, no APK work.

## Result

**C. GITHUB_PUSH_BLOCKED**

All five local branches are intact and reachable, no credentials are exposed,
and repository integrity is clean. Nothing could be pushed because GitHub
rejects write operations with HTTP 403.

## Branch / SHA table

| Branch | Local SHA | Remote SHA | Status |
|---|---|---|---|
| `cline/M0-008l-f-reproducible-build` | `c1dbe1f9c77257d8ec69c8a5c38621eea1483224` | _(absent)_ | LOCAL ONLY |
| `cline/M0-008l-g-libffi-cffi` | `a622ce3822d7ae36175b78e974e9ea7d494c5c5e` | _(absent)_ | LOCAL ONLY |
| `cline/M0-008m-httptools` | `d6eecc10c4c18121d7b834e941f02a97c2615b37` | _(absent)_ | LOCAL ONLY |
| `cline/M0-008n-dependency-audit-correction` | `b3432b095dd4b6830c907cb36ba9733dc284aaa6` | _(absent)_ | LOCAL ONLY |
| `cline/M0-008o-jiter` | `0903870c8aef5faea5c76811a480a930c4d0560c` | _(absent)_ | LOCAL ONLY |

## Refs

| Ref | Local SHA | Remote SHA | Status |
|---|---|---|---|
| `develop` | `6ec26ccb32bb842015cf441ae3c08ac0483930c8` | `6ec26ccb32bb842015cf441ae3c08ac0483930c8` | SYNCED |
| `main` | `cbecfa3dc349c7654feab4239ea431c3b6fd2ae4` | `cbecfa3dc349c7654feab4239ea431c3b6fd2ae4` | SYNCED |
| remote `HEAD` | - | `cbecfa3dc349c7654feab4239ea431c3b6fd2ae4` | points at `main` |

The remote's newest M0-008 branch is `cline/M0-008l-e-host-python-314`. The
five branches above have never existed on the remote.

## Authentication blocker

Evidence gathered, all read-only:

| Check | Result |
|---|---|
| remote URL contains a token | no, clean |
| `.git/config` contains token/pat/password/secret | no, clean |
| `credential.*` in `.git/config` | none |
| token value committed in any commit message | no |
| wheel / `.so` / APK / keystore tracked | no |
| `gradle-wrapper.jar` tracked | yes, pre-existing on remote branches, not a secret |

Why the push fails:

| Probe | Result |
|---|---|
| `git ls-remote origin` | **succeeds**, so the token authenticates for read |
| `git push` | **403 Permission denied** |
| `git push` with `credential.helper=` cleared and `GIT_ASKPASS` | **Authentication failed**, no anonymous write access |
| GitHub API `GET /user` with the same token | succeeds, authenticated as the repository owner |
| GitHub API `GET /repos/...` `permissions.push` | `true` |

So the token authenticates and the API reports write permission, yet git
writes are refused. That contradiction is a GitHub-side or token-side
authorisation problem, not a repository configuration problem and not
something this repository can fix.

No credential workaround was used. Specifically: no token was embedded in a
remote URL, no `git push <url-with-token>` was run, no force push, no history
rewrite, no merge, no squash, no rebase of shared history, and no branch was
deleted.

## Integrity verification

`git fsck --no-reflogs --full` reports **no errors, no missing objects and no
corruption**. Only dangling commits and trees are listed; these were left in
place and nothing was pruned.

All five milestone commits are reachable from their branch refs.

## M0-008N content verification

The authoritative correction is present in commit `b3432b0`:

| Requirement | Status |
|---|---|
| `docs/M0-008N_DEPENDENCY_AUDIT_CORRECTION.md` present | yes |
| orjson removed from the Hermes closure | yes, four independent checks recorded |
| jiter identified as the real Rust/PyO3 target | yes, via `openai==2.24.0` |
| resolution date recorded | yes, 2026-10-03 with the 14-day cutoff derivation |
| no accidental orjson build | yes, the commit touches **docs only** |

Files changed by `b3432b0`:

```
docs/M0-008H_CHAQUOPY_DEPENDENCY_CLOSURE.md      (correction appended in place)
docs/M0-008M_HTTPTOOLS_ANDROID_PROOF.md          (next-candidate note amended)
docs/M0-008N_DEPENDENCY_AUDIT_CORRECTION.md      (new)
```

No source, no build script, no binary. This documentation is preserved in
local history and is currently absent from GitHub.

## Required action to unblock

1. Revoke and reissue the token. Its value was printed into a terminal during
   diagnosis and must be treated as exposed.
2. Grant the new token `Contents: write` on `quyenbe0402/vit-ngan-harness`.
   If it is a fine-grained token, confirm the repository is selected in the
   token's resource list; a token scoped to the wrong resource authenticates
   but cannot write.
3. Push in dependency order, verifying each SHA:
   ``cline/M0-008l-f-reproducible-build`, `cline/M0-008l-g-libffi-cffi`, `cline/M0-008m-httptools``
   then `git ls-remote origin <branch>` and compare against the table above.

## Risk if this stays blocked

`M0-008N` carries the correction that removes `orjson` from the Hermes
dependency closure. While it exists only on this machine, any work resumed
from GitHub would still list `orjson` as a real Hermes blocker and could
duplicate roughly five milestones of work on a package that Hermes does not
require.
