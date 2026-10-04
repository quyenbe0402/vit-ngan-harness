# Pivot Execution Audit

Companion to `docs/PIVOT-DECISION.md`.

This audit asks one question about every execution-relevant artifact in the
repository: **does this still belong to the architecture that actually runs, and
if not, is anything silently running that should not be?**

Method: files were read, not inferred from names. Every finding cites the file and
the evidence that places it in its class. No file was deleted.

## Classification scheme

| Class | Meaning |
|---|---|
| **A. CURRENT_REMOTE_DEFAULT** | Part of the remote-Hermes default architecture |
| **B. SHARED_INFRASTRUCTURE** | Used by the default path today; would outlive any one architecture |
| **C. VERSION_B_PARKED** | Belongs to the preserved embedded/offline work; not executed by default |
| **D. HISTORICAL_RECORD** | Evidence of a past milestone; must not be edited |
| **E. OBSOLETE_EXECUTION_PATH** | Belongs to no current or parked architecture |
| **F. NEEDS_OWNER_DECISION** | Cannot be classified without an owner choice |

**Owner instruction honoured: nothing was deleted. Class E items are flagged
"requires explicit owner approval to remove".**

---

## Headline result

**Version-B infrastructure is NOT executing in the default path.** This was
verified, not assumed. A prior review asserted that the Chaquopy plugin was still
present in the production build and that CI still ran the embedded build. Both
claims were checked against the files and **both are false**.

The specific risk the owner asked about - parked machinery quietly running by
default - does not exist in this repository today.

---

## A. CURRENT_REMOTE_DEFAULT

**A-01 - `app/build.gradle.kts`**
Evidence: plugins are `android.application`, `kotlin.android`,
`kotlin.serialization`, `kotlin.compose`, `ksp`, `hilt`. There is **no
`com.chaquo.python` plugin**, no `chaquopy { }` block and no Python source set.
This is the production module and it carries no embedded-Python machinery.

**A-02 - `settings.gradle.kts` (root)**
Evidence: `include(":app")` only. No `includeBuild(...)`, no reference to
`probes/chaquopy-closure`. Therefore `./gradlew assembleDebug` at the repo root
does not configure the probe.

---

## B. SHARED_INFRASTRUCTURE

**B-01 - `core/hermes/` bridge, protocol adapter, transport abstraction**
Evidence: `HermesRuntime -> HermesBridge -> HermesProtocolAdapter ->
HermesTransport -> gateway`. Transport-agnostic by construction. This layer
survives the pivot unchanged: a remote gateway and an on-device gateway both sit
behind `HermesTransport`.

**B-02 - `core/policy/`, `core/workspace/`, `core/router/`, `core/diff/`**
Evidence: the trust chain (`ToolRouter -> CapabilityManager ->
TrustedPolicyEngine -> WorkspaceBroker -> SecurityPathResolver -> backend`) is
agent-facing and independent of where Hermes runs. Invariants S1-S9 in
`docs/AI_PROJECT_CONTEXT.md` remain valid under the remote architecture, because
the phone remains the policy authority even when the agent does not.

---


**B-03 - `.github/workflows/ci.yml`**
Evidence: runs `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` at the
repository root. `git grep -i 'probes|embedded|chaquopy'` over `.github` returns
no match. CI validates the production module only and is not an embedded-Python
path.

## C. VERSION_B_PARKED

**C-01 - `probes/chaquopy-closure/` (entire tree)**
Evidence, three independent facts:

1. It is a **separate Gradle build**: `probes/chaquopy-closure/settings.gradle.kts`
   declares `rootProject.name = "chaquopy-closure-probe"`. It is excluded from the
   root build graph (A-02).
2. It is **non-portable**: `app/build.gradle.kts` line 37 hardcodes
   `buildPython = ["C:/Users/ADMIN/AppData/Roaming/uv/python/cpython-3.14.6-windows-x86_64-none/python.exe"]`,
   an absolute path on one specific machine. A clean checkout on another machine
   cannot build it without editing a tracked file.
3. Its binary outputs are **gitignored** (`probes/.gitignore` excludes
   `native-wheels/*.whl`), so it is not reproducible from a clean checkout without
   re-running the documented NDK/Rust procedure.

Class **C**, not B. "Shared infrastructure" would imply the default path depends
on it; nothing in the evidence supports that. Preserved, not deleted - see OD-004.

**C-02 - `probes/build-env/Dockerfile`**
Evidence: the M0-008L-F reproducible Android build image definition. Belongs to
the Version-B toolchain. Not referenced by the production build or by CI.

**C-03 - `EmbeddedPythonRuntimeBackend.kt` and `RuntimeFeasibilityAudit.kt`**
Evidence: both compile into the production app. Both contained **factually false
claims** and were corrected in this pass - see the correction log below. They
remain stubs/data and are not a live runtime.

---

## D. HISTORICAL_RECORD

**D-01 - `docs/M0-007*` through `docs/M0-008O*` (26 milestone documents)**
Evidence: each is a milestone evidence record with device observations, ELF
analyses and artifact hashes. A `HISTORICAL RECORD` banner was added to each,
pointing at `PIVOT-DECISION.md`. **No historical content was altered** - the
banner pass was verified as pure insertion (+7 lines per file, zero deletions in
any M0 document).

**D-02 - `FakeHermesTransport.Companion.readyFrame` fixture**
Evidence: embeds a real captured `gateway.ready` frame from M0-007B, with a
comment recording the three ways the observed wire shape differed from the M0-006
assumption. This is captured wire evidence, not a runtime assumption. It remains
valid: the gateway emits that frame whether it runs remotely or locally.

---

## E. OBSOLETE_EXECUTION_PATH

**E-01 - `com.termux.permission.RUN_COMMAND` in `app/src/main/AndroidManifest.xml`**
Evidence: the permission is declared with a 15-line comment block referencing
`docs/M0-008A_TERMUX_IPC_BOUNDARY_AUDIT.md`.

Classification **E**: Termux is neither the remote default nor part of Version-B
(the Version-B asset is embedded CPython, not Termux). The permission belongs to a
third, older execution strategy.

**Correction to a prior review's claim:** that review stated this permission had
"no code references found". That is **inaccurate**.

Search scope was widened beyond `app/src/main` after that challenge, covering all
tracked `*.xml`, `*.kt`, `*.java` and `*.py` files, plus CI and Gradle scripts.
Findings:

- **Still declared: YES** - `app/src/main/AndroidManifest.xml:19` is the only
  tracked manifest declaring it.
- **7 code references**, all in
  `app/src/androidTest/java/dev/vitngan/harness/TermuxIpcBoundaryTest.kt`
  (lines 51, 62, 66, 69, 70, 77 and the class-level service check).
- **Zero references** in `app/src/main`, so production behaviour does not use it.
- No references in CI, Gradle scripts, ProGuard rules or any Python source.

**Consequence: removing the permission would break an existing instrumentation
test.** Any future removal must update `TermuxIpcBoundaryTest.kt` in the same
change.

**Requires explicit owner approval to remove.**

---

**E-02 - `termux-bridge/` module (7 tracked files) - MISSED BY THE FIRST AUDIT PASS**

**This finding was omitted from the first version of this audit and was caught by
an independent review, not by the original scan. Recording the omission is part of
the finding.**

Evidence: `git ls-files termux-bridge` returns 7 tracked files - `.gitignore`,
`app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`,
`app/src/main/java/dev/vitngan/termuxbridge/BridgeService.kt`, `build.gradle.kts`,
`gradle.properties`, `settings.gradle.kts`.

Its `AndroidManifest.xml` declares `android:sharedUserId="com.termux"` on the
`<application>` element, plus its own `dev.vitngan.termuxbridge.BRIDGE` dangerous
permission and an exported `BridgeService`. Its own comment states the purpose:
joining Termux' UID "is the only context that can run Hermes".

**Why the first audit missed it:** the original scan covered `app/` and `probes/`
only. `termux-bridge/` is a third top-level directory and was never in scope. This
is a scope defect in the audit, not an oversight in the repository.

Classification **E**: `sharedUserId="com.termux"` was **proven non-functional on
Android 16 by M0-008F** - the plugin received its own UID (`uid:10384` vs Termux'
`uid:10361`). The module implements a strategy already measured as blocked, and it
serves neither the remote default nor Version-B.

It is a **separate Gradle build** (`termux-bridge/settings.gradle.kts`) and is
**not** referenced by the root `settings.gradle.kts`, `.github` or the root
`build.gradle.kts`, so it does not execute in the default build. It carries its own
`compileSdk 35` / `targetSdk 28` and reads signing material from a gitignored
`signing.properties`.

**Requires explicit owner approval to remove.** Preserved, not deleted.

---

## F. NEEDS_OWNER_DECISION

**F-01 - Missing `android.permission.INTERNET`**
Evidence: `app/src/main/AndroidManifest.xml` declares exactly one
`uses-permission` (`com.termux.permission.RUN_COMMAND`). There is no
`android.permission.INTERNET`.

The remote architecture cannot function without it, and this audit does **not**
treat it as a routine omission. `git grep` over `app/src` finds **zero** network
code of any kind - no WebSocket client, no `ws://`/`wss://` literal, no
`http://`/`https://` literal except the XML namespace declaration, no
`networkSecurityConfig`, no `usesCleartextTraffic`, and no `res/xml` network
security file anywhere in the project.

**Therefore the remote thin-client architecture is decided but not implemented.**
Recording that distinction is the point of this finding: the pivot is a decision,
not a shipped capability. The permission was **not** added in this pass, because
there is no client code to use it and adding it is a code change outside a
documentation pass.

**F-02 - Local cache policy**
Evidence: `docs/PIVOT-DECISION.md` "Local Cache Policy" - OPEN. Read-only caching,
write-back caching and no caching are three different products.

**F-03 - Enum name `Feasibility.NOT_FEASIBLE` is semantically wrong**
Evidence: after the correction, the embedded-Python finding means "not selected",
not "not feasible". The name is now inaccurate in compiled production code.

**Deliberately not renamed.** `RuntimeFeasibilityAuditTest.kt:40` asserts
`assertEquals(Feasibility.NOT_FEASIBLE, finding.verdict)`. Renaming touches the
enum, the test and the audit's public shape. That is a refactor with test blast
radius, not a documentation fix, and it belongs to the owner.

**F-04 - Workspace concurrency and runtime-failure recovery**
Evidence: `docs/OPEN-DECISIONS.md` OD-005 - OPEN.

**F-05 - `RuntimeFeasibilityAudit.selected == RuntimeCandidate.TERMUX`**
---

## Correction log (this pass)

Files changed outside `docs/`, because they contained **factually false claims**
that would otherwise remain in compiled production code:

| File | False claim removed | Why it was false |
|---|---|---|
| `RuntimeFeasibilityAudit.kt` | "No Android CPython runtime reaches 3.14 ... the pinned native packages also publish zero Android wheels" | Disproven by M0-008L-G/M/O device evidence |
| `RuntimeFeasibilityAudit.kt` (KDoc) | "Embedded Python / Chaquopy is NOT FEASIBLE *for this revision*" | Same |
| `EmbeddedPythonRuntimeBackend.kt` (KDoc) | "Embedding CPython on Android is not viable ... not legally or reliably possible" | Same |
| `EmbeddedPythonRuntimeBackend.kt` (reason) | `"embedded CPython is not supported on Android"` | Same |

**What was deliberately NOT changed:** the `Feasibility` enum, the `NOT_FEASIBLE`
verdict value, `isSupported()`, `health()`, `start()` and `hermesActuallyRuns()`.
Those encode product decisions and are what the existing unit tests pin.

### Test-compatibility verification (checked BEFORE editing)

The edits were only safe because these were verified first:

| Assertion | Location | Effect of the edit |
|---|---|---|
| `finding.reason.contains("3.14")` | `RuntimeFeasibilityAuditTest.kt:41` | **Still satisfied** - the new reason retains the literal "CPython 3.14" |
| `assertEquals(Feasibility.NOT_FEASIBLE, ...)` | `RuntimeFeasibilityAuditTest.kt:40` | Untouched |
| `assertNotNull(py.unsupportedReason())` | `RuntimeBackendTest.kt:66`, `HermesBridgeTest.kt:64` | **Still satisfied** - new text is non-null |

Measured baseline after the edits: `:app:testDebugUnitTest` = **348 tests,
0 failures, 2 skipped**; `:app:assembleDebug` = **BUILD SUCCESSFUL**.

---

## What this audit does NOT cover

- The remote thin client **does not exist yet** (F-01). Any statement that the app
  "supports remote Hermes" would be false today.
- No runtime, deployment or M21 work was performed or planned here.
- The `probes/chaquopy-closure` wheel set was not rebuilt, re-verified or
  re-tested on device in this pass. Its Version-B status rests on the recorded
  milestone evidence, not on a fresh measurement.

---

## Correction log for THIS audit document itself

An independent review challenged this audit and found three defects in it. They
are recorded here rather than silently fixed.

**1. `termux-bridge/` was missed entirely (now E-02).**
The first scan covered `app/` and `probes/` only. `termux-bridge/` is a third
top-level directory with 7 tracked files, including a manifest declaring
`android:sharedUserId="com.termux"`. It was never in scan scope. That is a scope
defect in this audit.

**2. The E-01 reference search was too narrow.**
It originally reported "no references in `app/src/main`" and left the reader to
infer whether anything else referenced the permission. The search has since been
widened to all tracked `*.xml`, `*.kt`, `*.java`, `*.py`, CI and Gradle files,
and the result is now stated explicitly: still declared, 7 androidTest
references, 0 production references.

**3. The headline claim conflated two different assertions.**
The original headline said Version-B infrastructure is not executing in the
default path, "verified not assumed". Strictly, two distinct claims were being
bundled:

- *Verified:* `app/build.gradle.kts` contains no Chaquopy plugin, the probe is
  excluded from the root build graph, and CI runs only the root module. These
  are file-level facts established by reading the files.
- *Verified by unit test:* `EmbeddedPythonRuntimeBackend` reports
  `isSupported()=false`, `health()=UNSUPPORTED`, `start()=false`.
- *Not verified here:* that no runtime code path anywhere would invoke this
  backend. The unit tests assert an isolated class's return values; they do not
  prove what a selector or dispatcher consuming those values does. No
  instrumentation test boots the backend-selection flow.

`assembleDebug = BUILD SUCCESSFUL` demonstrates compilation only and is not
evidence of runtime behaviour. It is listed in this document as a build result,
not as runtime evidence.

**4. Weak-oracle caveat on the test result.**
`RuntimeFeasibilityAuditTest.kt:41` asserts `finding.reason.contains("3.14")`.
The corrected reason string deliberately retains the literal "CPython 3.14" so
this assertion still passes. That is a **substring check**, not a semantic one:
its passing shows the string was not emptied, and nothing more. It is not
independent confirmation that the new reason text is accurate.

**Named skipped tests.** The 2 skipped tests are both in
`SecurityPathResolverTest` (a path-security class unrelated to any file edited
in this pass):
- `symlink to a directory inside the root is allowed`
- one further symlink case in the same class

None of the edited classes (`RuntimeFeasibilityAuditTest`, `RuntimeBackendTest`,
`HermesBridgeTest`) had a skipped test, so "0 failures" is not doing less work
than it appears to in the vicinity of these edits.

**Banner-pass verification, as raw diff output rather than narration.**
`git diff --numstat HEAD~1 HEAD -- docs/M0-*.md` shows, for all 26 files, a
deletion count of **0**. 25 files show `7 0`; `M0-008G_RUNTIME_STRATEGY_PIVOT.md`
shows `10 0` (it received the SUPERSEDED banner instead of the generic one).
