> **HISTORICAL RECORD.**
> This document records the state and evidence at the time of its milestone.
> Its historical contents are preserved unchanged, including any conclusion
> later overtaken by newer evidence or by the architecture pivot. Do not read
> it as current architecture guidance.
> Current architecture decisions are governed by `docs/PIVOT-DECISION.md`.

# M0-008N Hermes Native Dependency Audit Correction

## Headline

**`orjson` is not a Hermes dependency at `eaecc99c`.** It must be removed from
the authoritative Android-active closure.

**`jiter` is**, reached through `openai==2.24.0`, and it is the correct
Rust/PyO3 target for the next native-wheel experiment.

This is a dependency-audit correction, not a build failure. Nothing was built
in this milestone.

---

## 1. Authoritative Hermes revision

```
NousResearch/hermes-agent @ eaecc99c
```

Every statement below was re-derived from that exact revision. Current `main`
was not used as a substitute.

Two independent retrievals were used:

- `pyproject.toml` at the raw revision URL
  `https://raw.githubusercontent.com/NousResearch/hermes-agent/eaecc99c/pyproject.toml`
  (48 350 bytes)
- the full source tarball
  `https://codeload.github.com/NousResearch/hermes-agent/tar.gz/eaecc99c`
  (6 807 552 bytes), scanned for dependency mentions

VERIFIED FROM SOURCE.

## 2. Corrected dependency graph for the Rust/PyO3 slot

```
Hermes @ eaecc99c
  `-- openai==2.24.0                      (direct pin, [project].dependencies)
        `-- jiter<1,>=0.10.0
              `-- RESOLVED: jiter 0.17.0
```

| Field | Value | Source |
|---|---|---|
| Hermes direct pin | `openai==2.24.0; python_version >= '3.14'` | `pyproject.toml` `[project].dependencies` |
| openai's requirement | `jiter<1,>=0.10.0` | PyPI metadata for `openai` 2.24.0 |
| resolved jiter | **0.17.0** | see "Resolution is a moving target" below |
| Python marker | inherited: `python_version >= '3.14'` | satisfied on 3.14.0 |
| Android-active | **YES** | no platform marker excludes Android |
| jiter's own dependencies | **none** (`requires_dist` is empty) | PyPI metadata |

VERIFIED FROM SOURCE.

### Why jiter is required

`openai` 2.24.0 lists `jiter<1,>=0.10.0` as a hard runtime dependency, not an
extra. The OpenAI SDK uses it to decode model responses. Hermes pins
`openai==2.24.0` directly, and its own comment in `pyproject.toml` states that
this pin "stays a direct pin because httpx/requests/openai each depend on
pydantic-core". So the OpenAI SDK is a startup dependency of Hermes, not an
optional provider path.

### Resolution is a moving target

Hermes sets:

```toml
[tool.uv]
exclude-newer = "14 days"
```

and `[tool.uv.exclude-newer-package]` does **not** contain `jiter`, so `jiter`
inherits the global relative window. A relative window means the resolved
version depends on **when** resolution runs.

The repository contains **no** `uv.lock`, `requirements.txt` or `poetry.lock`
(scanned the full tarball). The resolution is therefore **not reproducible from
the repository alone**.

For completeness, the resolution as of the date of this milestone:

```
today            2026-10-03
14-day cutoff    2026-09-19
jiter releases at or before cutoff:
  2026-09-12   0.17.0     <- newest eligible
  2026-06-29   0.16.0
  2026-05-19   0.15.0
  2026-04-10   0.14.0
releases excluded by the window: none
```

**Resolved: `jiter==0.17.0` as of 2026-10-03.** Any future experiment must
re-record this date, because a release published after the cutoff would change
the answer.

Note that `openai = false` appears in `[tool.uv.exclude-newer-package]`, which
disables the window for `openai` specifically. That does not affect `jiter`.

VERIFIED BY BUILD.

## 3. `orjson` is NOT a Hermes dependency

Four independent checks, all negative.

**a) `pyproject.toml` search.** The string `orjson` does not occur anywhere in
the file. Not in `[project].dependencies` (46 entries), not in
`[project.optional-dependencies]`, not in `[dependency-groups]`, not in
`[tool.uv.override-dependencies]`, not in `[tool.uv.exclude-newer-package]`.

**b) Full source search.** All 426 text files (`.py`, `.toml`, `.cfg`, `.txt`,
`.md`, `.yaml`, `.yml`) in the `eaecc99c` tarball were read and searched.
**Zero** files mention `orjson`. There is no `import orjson` anywhere in the
Hermes codebase.

**c) Transitive reachability.** Every package name appearing in the
`pyproject.toml` was enumerated (116 candidates) and its PyPI metadata checked
for a requirement on `orjson`. Exactly one matched:

```
discord.py -> orjson>=3.5.4; extra == "speed"
```

**d) The one candidate path is not taken.** Hermes requests
`discord.py[voice]==2.7.1`, in the `discord` optional extra and in the
`messaging` extra. It never requests the `speed` extra. Searching the whole
`pyproject.toml` for `,speed]` returns nothing.

VERIFIED FROM SOURCE.

### Consequence

| Package | Status in Hermes closure at `eaecc99c` |
|---|---|
| `jiter` | **IN**, via `openai==2.24.0` |
| `orjson` | **NOT IN** |
| `charset-normalizer` | not verified either way; removed from the blocker list pending a proper audit |

`orjson` was carried through M0-008H, M0-008I, M0-008J and into the M0-008M
"next candidate" note. That chain of references is now superseded.

## 4. Documentation corrections applied

Historical commits were not rewritten. Corrections were appended in place so
the audit trail is preserved and the earlier text stays readable as history.

| File | Change |
|---|---|
| `docs/M0-008H_CHAQUOPY_DEPENDENCY_CLOSURE.md` | correction block appended under the "absent from Chaquopy" list: `jiter` confirmed, `orjson` retracted, `charset-normalizer` unverified |
| `docs/M0-008M_HTTPTOOLS_ANDROID_PROOF.md` | "Next candidate" section amended: `orjson` retracted, `jiter` substituted |
| `docs/M0-008L-F_REPRODUCIBLE_BUILD_ENVIRONMENT.md` | not amended; it never named `orjson` as a target |

### A tooling mistake made and caught during this milestone

While applying the M0-008H correction, a file named
`docs/M0-008H_CHAQUOY_DEPENDENCY_CLOSURE.md` was created with a misspelled name
(`CHAQUOY` instead of `CHAQUOPY`) containing only the correction text. The real
document was left untouched. `git status` exposed the new file as untracked
while the tracked document showed no modification, which is what surfaced the
mistake.

The stray file was deleted and the correction was applied to the real tracked
document instead, as a 16-line insertion under the original line. No history
was rewritten. This is recorded rather than silently fixed because it is the
same class of error as the earlier probe mistakes: an artifact that looks
correct in isolation but is not the thing being tested.

## 5. jiter source inspection (read only, nothing built)

Obtained for scoping only.

```
sdist      jiter-0.17.0.tar.gz
sha256     03e432f226a453851079fb84cd17c6da9991eab723e28d716f14ae3d906e0c12  (PyPI-published, matches locally computed)
reference wheel (aarch64, cp314)
           jiter-0.17.0-cp314-cp314-manylinux_2_17_aarch64.manylinux2014_aarch64.whl
```

VERIFIED FROM SOURCE.

| Property | Value | Source |
|---|---|---|
| build backend | `maturin>=1.15.0,<2` | `pyproject.toml` `[build-system]` |
| Rust edition | 2024 | workspace `Cargo.toml` |
| Rust MSRV | 1.88 (`# MSRV should match pydantic-core`) | workspace `Cargo.toml` |
| PyO3 | **0.29.2** | `[workspace.dependencies] pyo3 = { version = "0.29.2" }` |
| pyo3-build-config | 0.29.2 | workspace |
| pyo3 features | `num-bigint` | `crates/jiter-python/Cargo.toml` |
| python crate | `jiter` with features `python`, `num-bigint` | `crates/jiter-python/Cargo.toml` |
| lib name / crate-type | `jiter_python`, `["cdylib", "rlib"]` | `[lib]` |
| requires-python | `>=3.10` | `[project]` |
| classifiers | includes `Programming Language :: Python :: 3.14` | `[project]` |
| declared dependencies | none on PyPI | PyPI metadata |

VERIFIED FROM SOURCE.

### Android implications

- **PyO3 0.29.2 is the same major/minor line already proven.** `cryptography`
  50.0.1 locked `pyo3 0.29.0`. The `PYO3_CONFIG_FILE` mechanism established in
  M0-008L-G applies unchanged.
- **MSRV 1.88 is satisfied** by the image's pinned Rust 1.99.0.
- **No C or C++ sources.** `crates/jiter` is pure Rust. There is no
  `build.rs` vendoring a third-party C library, no OpenSSL, no libffi, no
  libsodium. This matches the constraint that the experiment must not introduce
  any new native dependency.
- **maturin is the backend**, and `[tool.uv.exclude-newer-package]` in Hermes
  lists `maturin = false`, so the backend itself is not version-capped there.
- One risk to watch: in M0-008L-C, driving `maturin build --target
  aarch64-linux-android` failed with `Could not find _sysconfigdata*.py`. That
  was before the development tree was generated. The image now contains
  `/build/work/pydev/lib/_sysconfigdata__android_aarch64-linux-android.py`,
  so the earlier blocker is expected to be resolved, but it must be re-verified
  rather than assumed.

## 6. Proven pipeline to reuse

Verified present in image `m008l/build-env:2` and container `m008lbuild2`:

| Component | State |
|---|---|
| host CPython 3.14.0 | `/opt/python314` |
| Rust 1.99.0 + `aarch64-linux-android` target | `/opt/cargo` |
| Android NDK r27c (27.2.12479018) | volume `ndkcache` at `/opt/android` |
| Android CPython 3.14 dev tree | `/build/work/pydev/include` (`ANDROID_API_LEVEL 24`, zero host contamination) |
| real Chaquopy `libpython3.14.so` | `/build/work/pydev/lib`, sha256 `4b99ed3a...` |
| `_sysconfigdata` for Android | `/build/work/pydev/lib/_sysconfigdata__android_aarch64-linux-android.py` |
| PyO3 cross config | `/build/work/pyo3_config.txt` (`shared=true`, `lib_name=python3.14`, `lib_dir=/build/work/pydev/lib`) |
| linker wrapper | `/build/work/linkwrap/aarch64-clang` |
| OpenSSL 3.0.18 Chaquopy prefix | `/build/work/ossl` (not needed for jiter) |

VERIFIED ON DEVICE previously; re-checked in this milestone for Rust version
and the dev-tree contents.

## 7. Next build milestone, precisely scoped

**Target:** `jiter==0.17.0`, as resolved on 2026-10-03. Re-confirm the
resolution date at execution time, because `exclude-newer = "14 days"` is
relative.

**Question:** does the proven Rust + PyO3 + CPython 3.14 + Android arm64
pipeline work for a package whose build backend is maturin rather than
setuptools-rust, and whose crate layout is a two-crate workspace?

**Rules:**

1. No OpenSSL, no libffi, no libsodium, no Termux, no unrelated native
   library. If jiter turns out to need one, stop and characterise it before
   stacking any fix.
2. No Hermes import, no Hermes modification.
3. No Python downgrade, no cp313 substitution, no Linux aarch64 wheel
   substitution, no `--no-deps`.
4. Any source patch must be reported before it is applied.
5. Build inside the existing image; do not recreate the environment.
6. Negative control and artifact SHA chain as already established:
   build = APK = device, with `UP-TO-DATE` never accepted as evidence.

**Expected shape of the build:**

```
cargo build --release --target aarch64-linux-android -p jiter-python
  env: PYO3_CONFIG_FILE=/build/work/pyo3_config.txt
       PYO3_CROSS_LIB_DIR=/build/work/pydev/lib
       PYO3_CROSS_PYTHON_VERSION=3.14
       CARGO_BUILD_TARGET=aarch64-linux-android
       CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=$TC/aarch64-linux-android24-clang
       CC_aarch64_linux_android / CFLAGS_aarch64_linux_android -> /build/work/pydev/include
```

Expected module name is `jiter_python` (the `[lib] name`), which maturin
renames to `jiter`. That renaming must be verified against the built artifact,
not assumed, given the packaging lessons in M0-008L-G and M0-008M.

**Acceptance criteria:** ELF64 / AArch64, `DT_NEEDED libpython3.14.so` with no
unexpected library, `NOTYPE == 0` for CPython references, complete official
package contents, verified SHA chain, and real native JSON parsing plus
deterministic invalid-input rejection on the physical device.

## 8. Git

```
branch   cline/M0-008n-dependency-audit-correction
commit   docs(runtime): correct Hermes native dependency audit
```

Nothing was built, so no binary, APK, wheel or credential was produced or
committed. Production code is untouched.

Push status is reported from `git ls-remote`, not from the exit code of a push
attempt. As of this milestone the token available in the environment is
rejected by GitHub with HTTP 403 for this repository, so the branch is
expected to be **LOCAL ONLY**. Earlier milestones (M0-008F through M0-008L-E)
are present on the remote; M0-008L-F, M0-008L-G and M0-008M are not.
