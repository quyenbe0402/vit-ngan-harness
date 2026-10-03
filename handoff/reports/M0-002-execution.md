# M0-002 Execution Report

**Objective:** domain contracts + security foundation for `vit-ngan-harness`.
**Agent:** Cline (executor). **Advisor:** GPT checkpoints UNAVAILABLE — see below.
**Branch:** `claude/M0-002-domain-contracts` **Base:** `develop` @ `d070fa7` (+ M0-001 `0f18167`)

---

## 1. GPT advisor — UNAVAILABLE (not simulated)

The brief specifies GPT checkpoints A–E as an **independent** architecture
and review advisor. No such capability exists in this environment; the
available subagent tool runs the same model as the executor and therefore is
not an independent reviewer. Checkpoints are reported as UNAVAILABLE rather
than faked.

Review was performed instead against the nine objective security invariants
in `docs/AI_PROJECT_CONTEXT.md` §3, using executable evidence (below) rather
than opinion.

## 2. Base-state error worth recording

M0-001 was committed to `claude/M0-001-foundation` and **never merged into
`develop`**. M0-002 was initially branched from `develop`, which contained no
Gradle project at all — `settings.gradle.kts`, `gradlew`, `gradle.properties`
and `app/build.gradle.kts` were absent. Detected before writing code and
repaired by merging `0f18167` into the M0-002 branch.

**Recommendation:** M0-001 should be merged into `develop` so the integration
branch is buildable. This is a repository-topology issue, not a code issue.

## 3. Files created (39 main + 8 test)

| Package | Types |
|---|---|
| `core.policy` | Capability, PolicyDecision, DenyCode, AppPolicy, DenyRule, UserAuth, TrustedPolicyEngine, CapabilityManager |
| `core.workspace` | CanonicalPath, PathRejection, PathResolution, FileSystemAccess, SecurityPathResolver, WorkspaceBackendType, WorkspaceDescriptor, FileMeta, BackendResult, WorkspaceBackend, AppPrivateBackend, WorkspaceBroker, BrokerResult |
| `core.router` | ToolCall, ToolResult, ToolRouter |
| `core.task` | Task, TaskStatus, TaskManager, TaskManagerImpl |
| `core.event` | EventEnvelope, EventFilter, EventBus, EventBusImpl, Subscription |
| `core.runtime` | RuntimeHealth, BridgeMessage, ProcessEvent, HermesRuntime, TermuxRuntimeBackend, EmbeddedPythonRuntimeBackend, RuntimeManager, ProcessManager, ProcessRunner, LaunchResult |
| `core.context` | ContextFile, ContextPacket, RepositoryContextEngine |
| `core.persistence` | Checkpoint, CheckpointEntity, AppDatabase, CheckpointManager |
| `core.diff` | DiffHunk, DiffResult, DiffEngine |

Modified: `gradle/libs.versions.toml` (added junit + coroutines-test),
`app/build.gradle.kts` (added `testImplementation` block).

## 4. Commands executed

```
git checkout -b claude/M0-002-domain-contracts
gradlew :app:compileDebugKotlin
gradlew :app:testDebugUnitTest
gradlew :app:assembleDebug
.\scripts\doctor.ps1 / android-check.ps1 / device-check.ps1
```

## 5. Test result

```
DiffEngineTest            tests=10  fail=0
EventEnvelopeTest         tests=12  fail=0
CapabilityManagerTest     tests=6   fail=0
TrustedPolicyEngineTest   tests=10  fail=0
ToolRouterTest            tests=10  fail=0
HermesBridgeTest          tests=9   fail=0
TaskStateMachineTest      tests=10  fail=0
SecurityPathResolverTest  tests=24  fail=0  skip=2
TOTAL: 91 tests, 0 failures, 2 skipped
```

The 2 skips are real-filesystem symlink tests: Windows requires Developer Mode
or elevation to create symlinks. The symlink-escape defence is still pinned
deterministically by a test that drives the `FileSystemAccess` seam with a
lexically-in-bounds path whose real location is outside the root.

Coverage required by the brief: `../` traversal, `../../` traversal, absolute
escape (Unix + Windows drive + UNC), root-prefix collision, symlink escape,
authorised in-root path, workspace policy tampering, capability denial, policy
denial, router ordering, task illegal transition, event JSON roundtrip,
diff additions/removals — all present.

## 6. Build result

```
:app:compileDebugKotlin  BUILD SUCCESSFUL
:app:testDebugUnitTest   BUILD SUCCESSFUL
:app:assembleDebug       BUILD SUCCESSFUL in 39s
APK: app\build\outputs\apk\debug\app-debug.apk  (9930.57 KB)
```

## 7. Device result — NOT CONNECTED

`adb devices` returned an empty list at validation time (the ADB daemon had
restarted and the phone was not re-attached). `device-check.ps1` correctly
reported `NOT_CONNECTED` and refused to claim success.

**No device validation was performed in this milestone**, which is recorded
rather than papered over. M0-002 is pure domain/security logic with no Android
runtime behaviour, so this does not block completion; the first device-dependent
milestone will need the phone connected.

## 8. Defects found and fixed (all found by running, not reading)

1. **`WorkspaceBroker` non-exhaustive `when`** — compile failure.
2. **Windows separator mismatch in `SecurityPathResolver`** — `canonicalPath`
   returns `\`, the root was normalised to `/`, so *every legitimate path* was
   rejected as a symlink escape. 10 tests failed. Fixed by normalising both
   sides before comparison.
3. **`CanonicalPath` split only on `/`** — on Windows `fileName` returned the
   whole path and `relativeToRoot` returned `\sub\nested.txt`. Fixed to handle
   both separators.
4. **`DenyRule` failed to match relative paths** — a rule on `/secrets` did not
   match `secrets/a.txt`. This was a *security* defect: a deny rule could be
   bypassed by dropping a leading slash. Fixed with boundary-aware
   normalisation on both sides.
5. **Policy/package confusion** — the engine trusted whatever the provider
   returned. Added a check that the policy names the requesting package.
6. **`AppPrivateBackend.contains` separator bug** — `/`-only check rejected
   every path on Windows. Fixed.
7. **`DiffEngine` LCS loop consumed unchanged lines** — an addition was
   reported as an extra removal. Fixed by judging each side against the LCS
   independently.
8. **`String.lines()` trailing empty element** — a phantom diff line and
   shifted hunk offsets. Fixed.
9. **`java-diff-utils` API mismatch** — the library is Kotlin-idiomatic-awkward
   (`delta.lines()`); after two failed attempts the diff was reimplemented
   with a local LCS, which removed the API risk and the now-unused dependency.

## 9. Security invariant verification (executed, not asserted)

| Invariant | Evidence |
|---|---|
| S1 single route to files | `ToolRouterTest` denies traversal at the router; broker is the only I/O path |
| S2 ProcessManager resolves no paths | holds no `SecurityPathResolver`, no root |
| S3 no raw-String path | the only `launch(...)` overload takes `CanonicalPath` |
| S4 argument lists, no shell | grep for `sh -c`/`ProcessBuilder`/exec in main: only KDoc prose |
| S5 File I/O confined | `readText/writeText/listFiles/delete` appear **only** in `AppPrivateBackend` |
| S6 policy outside workspace | `AppPolicy` lives in the trusted policy module, unreachable from workspace content |
| S7 workspace content untrusted | `EventEnvelope`/`ContextFile` carry data only; nothing derives authority |
| S8 Hermes never authorises | `TermuxRuntimeBackend.send` and `EmbeddedPythonRuntimeBackend.send` both return false; `HermesBridgeTest` pins it |
| S9 traversal defence | 24 tests incl. Unix/drive/UNC/collision/symlink/NUL |

## 10. Remaining risks

1. **M0-001 not merged into `develop`** — see §2.
2. **Symlink tests skip on Windows** — mitigated by the seam test; ideally run
   the suite once on a device or in CI on Linux to exercise real symlinks.
3. **Build-tools 34.0.0 was auto-installed** during the first build; the SDK
   now differs from the recorded baseline. Pin `buildToolsVersion`.
4. **`android.suppressUnsupportedCompileSdk=36` warning** — AGP 8.7.3 predates
   SDK 36; benign today, but pin deliberately.
5. **`DiffEngine` is O(n·m)** — fine for context-sized files, not for large
   repositories. Revisit if context packets grow.
6. **No device validation** — see §7.

## 11. Next milestone

M0-003 — not started. `AppDatabase` has one entity and no DAO; the checkpoint
store is in-memory behind an interface. Hermes remains stubbed by design.