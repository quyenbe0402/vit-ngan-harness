# M0-008H Chaquopy Dependency Closure Probe

## Headline

**B. PARTIALLY FEASIBLE - CLOSURE BLOCKERS IDENTIFIED**

The Hermes closure **cannot** be built under Chaquopy 17.0 + Python 3.14 on
Android arm64. Two independent, verified blockers were found. The second one is
structural and not fixable by configuration.

---

## 1. Exact versions

| Item | Value | Class |
|---|---|---|
| Hermes revision | `eaecc99c` (NousResearch/hermes-agent) | VERIFIED FROM SOURCE |
| Hermes `requires-python` | `">=3.11,<3.15"` | VERIFIED FROM SOURCE |
| Chaquopy plugin | `17.0.0` (pinned, not dynamic) | VERIFIED BY BUILD |
| Chaquopy runtime Python | 3.14 | VERIFIED BY BUILD |
| `buildPython` | CPython 3.14.6 (host) | VERIFIED BY BUILD |
| Device ABI | `arm64-v8a` | VERIFIED BY BUILD |
| minSdk / targetSdk | 24 / 34, compileSdk 36 | VERIFIED BY BUILD |
| Chaquopy plugin's own version list | `[3.10, 3.11, 3.12, 3.13, 3.14]` | VERIFIED BY BUILD |

Chaquopy 17.0 **does** accept Python 3.14. The M0-008G correction holds: the
old "Chaquopy stops at 3.13" claim is wrong.

Note a trap that was avoided: Hermes requires `>=3.11,<3.15`, but **every**
dependency carries the marker `python_version >= '3.14'`. Building on Python
3.13 would install **none** of Hermes's dependencies while still appearing to
succeed. The probe pinned 3.14 deliberately.

---

## 2. Recomputed dependency list

Recomputed directly from `pyproject.toml` at `eaecc99c`, not copied from a
secondary document. 53 raw entries, 52 unique strings.

**The previously cited "45 core dependencies" was wrong.** The real
Android-active set is **36** entries. `win32`-only markers
(`tzdata`, `pywinpty`, `pywin32`, four `winrt-*`, `concurrent-log-handler`)
never apply. `uvloop` and `nemo-relay` are excluded on Android by their own
markers (`sys_platform != 'android'`, `'android' not in platform_release`).
`spacy-curated-transformers` is pinned to `sys_platform == 'never'`.

---

## 3. Resolution result per dependency

Method: `pip install --dry-run --only-binary=:all: --platform
android_34_arm64_v8a`, indexes `pypi.org/simple` + `chaquo.com/pypi-13.1`,
subtracting blockers until the resolver stopped complaining. This reproduces
Chaquopy's own install semantics exactly, because Chaquopy 17.0 uses
`--only-binary` with that platform tag and that native index.

### Confirmed blockers

| # | Dependency | Failure | Class |
|---|---|---|---|
| 1 | `firecrawl-anydoc==0.2.4` | `No matching distribution found` - not on PyPI at all | VERIFIED BY BUILD |
| 2 | `cryptography==50.0.1` | no wheel for `android_34_arm64_v8a` | VERIFIED BY BUILD |
| 3 | `httptools>=0.6.3,<0.9` | no wheel for `android_34_arm64_v8a` | VERIFIED BY BUILD |
| 4 | `watchfiles>=0.20,<2` | only `0.0.0a1` available for the platform | VERIFIED BY BUILD |
| 5 | `Pillow==12.3.0` | no wheel for `android_34_arm64_v8a` | VERIFIED BY BUILD |
| 6 | `pynacl>=1.6,<1.7` | no wheel for `android_34_arm64_v8a` | VERIFIED BY BUILD |
| 7 | `jiter` (transitive, from `openai`) | `No matching distribution found` | VERIFIED BY BUILD |
| 8 | `pydantic-core==2.46.4` (transitive, from `pydantic`) | only `0.0.1` matches; PyPI ships **zero** Android wheels for this release | VERIFIED BY BUILD |
| 9 | `pillow-heif>=1.4.0,<2` | absent from Chaquopy's native index | VERIFIED BY BUILD |
| 10 | `resvg-py==0.4.0` | absent from Chaquopy's native index | VERIFIED BY BUILD |

### Packages that resolve cleanly (pure Python)

`openai`, `certifi`, `truststore`, `python-dotenv`, `fire`, `httpx`, `rich`,
`tenacity`, `tomli-w`, `ruamel.yaml`, `requests`, `jinja2`, `prompt_toolkit`,
`croniter`, `snowballstemmer`, `packaging`, `Markdown`, `urllib3`,
`browser-harness`, `pathspec`, `fastapi`, `uvicorn`, `python-multipart`,
`ptyprocess`, `websockets`.

`psutil` is special: Hermes declares it for Android as a **git VCS**
dependency, pinned to commit `380bd2b5`. Chaquopy 17.0 **does** perform the
`git clone`/`rev-parse`/`checkout` correctly - VERIFIED BY BUILD - so VCS
installs are not themselves a blocker.

---

## 4. Native ABI results

This is the structural finding.

**Chaquopy's native repository `chaquo.com/pypi-13.1` contains 134 packages.**
Hermes needs at least six native packages that are **absent from it entirely**:

- `pydantic-core` - **absent**
- `httptools` - **absent**
- `watchfiles` - **absent**
- `pillow-heif` - **absent**
- `resvg-py` - **absent**
- `orjson` / `charset-normalizer` / `jiter` - **absent**

### The cp314 gap - the decisive finding

For the native packages Chaquopy *does* ship, the available CPython ABI tags
are:

| Package | ABI tags present | cp314 arm64 wheels |
|---|---|---|
| cryptography | cp38 cp39 cp310 cp311 cp312 cp313 | **0** |
| pillow | cp38 cp39 cp310 cp311 cp312 cp313 | **0** |
| pynacl | cp38 cp39 cp310 cp311 cp312 cp313 | **0** |
| psutil | cp38 cp39 cp310 cp311 cp312 cp313 | **0** |

Example of what does exist:
`cryptography-3.3.2-0-cp38-cp38-android_21_arm64_v8a.whl`.

**There is not a single `cp314` Android wheel for any native package in
Chaquopy's repository.** Chaquopy 17.0 can *run* Python 3.14, but its native
package repository stops at cp313.

This is not a configuration error and cannot be fixed by editing
`build.gradle.kts`. It is a supply-chain gap.

---

## 5. Node analysis

**NOT REQUIRED for the tested path.** Hermes' Python runtime and its dependency
closure are pure-Python plus native wheels. Node is relevant only to specific
optional tool integrations, not to `import` or runtime construction.

Class: **OPTIONAL / REQUIRED FOR FEATURE**. Not evaluated further because the
build fails long before any Node-dependent code is reached.

---

## 6. subprocess analysis

**UNKNOWN / NOT REACHED.** Hermes uses `subprocess` (via `ptyprocess`) for
interactive shell tooling. Android blocks `execve` of foreign binaries, and no
shell is present in the app sandbox. Since the closure never builds, this was
not measured on device and is recorded as UNKNOWN rather than guessed.

---

## 7. Filesystem analysis

**NOT REACHED.** The probe would have exercised app-private storage and SQLite
WAL. The APK was never produced, so nothing was measured. UNKNOWN.

---

## 8. SQLite / WAL

**NOT REACHED.** See above. UNKNOWN. No claim is made.

---

## 9. Network

**NOT REACHED.** No provider requests were made and no credentials were added.
UNKNOWN.

---

## 10. MCP / plugins / skills

**NOT INTEGRATED**, per instruction. From the closure, the feature groups that
would block later integration are:

| Group | Blocker |
|---|---|
| Image/document handling (`Pillow`, `pillow-heif`, `resvg-py`) | all three unavailable |
| Crypto (`cryptography`, `PyJWT[crypto]`, `pynacl`) | unavailable |
| Config/validation (`pydantic`) | `pydantic-core` unavailable |
| Web server (`fastapi`, `uvicorn`, `httptools`) | `httptools` unavailable |
| File watching (`watchfiles`) | unavailable |
| LLM SDK (`openai`, `jiter`) | `jiter` unavailable |

---

## 11. APK size

**NOT MEASURED.** No APK was produced, because the dependency closure fails.
Any size figure here would be invented.

---

## 12. Physical-device results

**NOT REACHED.** No probe APK was installed and no instrumentation ran,
because the build fails at `:app:installDebugPythonRequirements`.

Device: Xiaomi Redmi 24069RA21C, Android 16 / API 36, arm64-v8a.

Page size was not measured, since no native wheel was ever loaded. UNKNOWN.

---

## 13. Hermes import result

**NOT REACHED.** Hermes was never copied into an APK and never imported.
Class: **NOT TESTED**.

## 14. Hermes initialization result

**NOT REACHED.** Class: **NOT TESTED**.

---

## 15. Blockers

**Blocker 1 (package availability).** Nine dependency requirements cannot be
satisfied for `android_*_arm64_v8a` under `--only-binary`.

**Blocker 2 (ABI gap, structural).** Chaquopy's native repository has **no
cp314 Android wheels at all**. Even if every missing package were vendored,
the versions that Chaquopy *does* ship are ABI-tagged only up to cp313.

Additionally, `firecrawl-anydoc==0.2.4` is pinned at a version that does not
exist on PyPI. **VERIFIED FROM SOURCE** that Hermes pins it;
**VERIFIED BY BUILD** that it cannot be resolved. That is an upstream
dependency defect independent of Android.

---

## 16. Proven capabilities

- Chaquopy 17.0 loads and accepts `version = "3.14"`. VERIFIED BY BUILD.
- Chaquopy 17.0 performs **git VCS installs** (psutil from a pinned commit).
  VERIFIED BY BUILD.
- Pure-Python portion of the Hermes closure (~26 packages) resolves cleanly for
  `android_34_arm64_v8a`. VERIFIED BY BUILD.
- Chaquopy's native repo does provide Android arm64 wheels, just not cp314 ones.

## 17. Unknowns

- Whether Chaquopy will ship cp314 native wheels in a future release.
- Whether vendoring cp313-built wheels into a cp314 interpreter is possible
  (almost certainly not: CPython ABI is not stable across minor versions).
- Whether Hermes' startup path actually needs Node, subprocess or SQLite WAL,
  since none of that was reached.
- APK size for a working configuration.

## 18. Recommended next experiment

Do **not** patch packages to force a green closure. That would produce a
misleading result, which is exactly what this milestone exists to prevent.

The honest next experiment is narrow:

1. Confirm with Chaquopy's maintainers whether cp314 native wheels are planned
   and on what timeline. This is a **supply-chain** question, not a technical
   one, and it gates everything else.
2. In parallel, evaluate the two paths that do not depend on it:
   - Hermes' dependency set with a **relaxed** Python floor, if upstream is
     willing. `pydantic`, `cryptography` and `Pillow` all have cp313 Android
     wheels in Chaquopy's repo, so **Python 3.13 is a genuinely viable
     fallback** - but every Hermes dependency is gated on
     `python_version >= '3.14'`, so this needs an upstream change.
   - Candidate D, the remote runtime, which has no native-wheel problem at
     all.

## 19. Result classification

**B. PARTIALLY FEASIBLE - CLOSURE BLOCKERS IDENTIFIED**

Explicitly **not** "A. HERMES LOAD PROVEN": Hermes was never imported.
Explicitly **not** "FEASIBLE": Python 3.14 running is not sufficient, and the
closure does not build.

---

## Stop-condition answers

**1. Can all required Hermes dependencies run under Chaquopy/Python 3.14 on
arm64 Android?**
**No.** Ten requirements are unresolvable, and the native repository has no
cp314 wheels at all.

**2. Can Hermes itself import?**
**Unknown - never tested.** No APK was produced.

**3. Can Hermes initialize?**
**Unknown - never tested.**

**4. First unavoidable blocker?**
Chaquopy 17.0's native package repository contains **no cp314 Android
wheels**. This blocks `cryptography`, `Pillow` and `pynacl` even at versions
Chaquopy does ship, and is a supply-chain gap rather than a build
misconfiguration.

**5. Is Candidate A now?**
**PARTIAL** - and materially weaker than M0-008G assumed. The gap is native ABI
supply, which is upstream of anything this project controls.

---

## Corrections

- M0-008G called Candidate A **PARTIALLY FEASIBLE** with native ABI named as the
  risk. That was directionally right but understated: the risk is not
  per-package recipe work, it is an **ABI-level supply gap** at cp314.
- The "45 core dependencies" figure is corrected to 36 Android-active entries,
  recomputed from source.
