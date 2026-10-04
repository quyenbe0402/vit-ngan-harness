> **HISTORICAL RECORD.**
> This document records the state and evidence at the time of its milestone.
> Its historical contents are preserved unchanged, including any conclusion
> later overtaken by newer evidence or by the architecture pivot. Do not read
> it as current architecture guidance.
> Current architecture decisions are governed by `docs/PIVOT-DECISION.md`.

# M0-008O jiter 0.17.0 Android Native Proof

## Headline

**A. JITER NATIVE PATH PROVEN**

`jiter` is the actual Rust/PyO3 dependency of Hermes at `eaecc99c`, reached
through `openai==2.24.0`. It was rebuilt for CPython 3.14 / Android arm64,
packaged by Chaquopy 17.0, and executed on the physical device.

This is the first direct closure test in the series: the target was selected
from Hermes' own dependency graph rather than from a list of suspected
packages.

---

## 1. Hermes dependency chain

```
Hermes @ eaecc99c
  `-- openai==2.24.0                      (direct pin, [project].dependencies)
        `-- jiter<1,>=0.10.0
              `-- jiter==0.17.0
```

| Field | Value |
|---|---|
| Hermes pin | `openai==2.24.0; python_version >= '3.14'` |
| openai requirement | `jiter<1,>=0.10.0` (hard dependency, not an extra) |
| resolution date | **2026-10-03** |
| resolved | **jiter 0.17.0** |
| Android-active | YES, no platform marker excludes Android |
| jiter's own deps | none |

VERIFIED FROM SOURCE.

## 2. Resolution date and reproducibility

Hermes sets `exclude-newer = "14 days"` and does **not** override it for
`jiter`. There is no lockfile in the repository. The resolution is therefore
date-dependent, and this milestone records the date explicitly.

Re-derived independently in this milestone from PyPI upload timestamps:

```
today         2026-10-03
cutoff        2026-09-19   (today minus 14 days)
jiter 0.17.0  uploaded 2026-09-12   <- newest eligible
jiter 0.16.0  uploaded 2026-06-29
```

The reproduction matched the M0-008N audit, so the target was not silently
changed. VERIFIED BY BUILD.

## 3. Source provenance

```
sdist   jiter-0.17.0.tar.gz
sha256  03e432f226a453851079fb84cd17c6da9991eab723e28d716f14ae3d906e0c12
        (PyPI-published digest, matches the locally computed value)

reference wheel (aarch64, cp314)
        jiter-0.17.0-cp314-cp314-manylinux_2_17_aarch64.manylinux2014_aarch64.whl
sha256  dc0288ce39190ee33fe6e4ec73161eed34e7e2da509b525546ca061778d62b64
```

VERIFIED FROM SOURCE.

## 4. Rust, PyO3 and backend versions

Re-read from the sdist rather than reused from the audit:

| Property | Value | Source |
|---|---|---|
| build backend | `maturin>=1.15.0,<2` | `pyproject.toml` `[build-system]` |
| maturin used | **1.15.0** | build environment preflight |
| Rust edition | 2024 | workspace `Cargo.toml` |
| Rust MSRV | 1.88 | workspace `Cargo.toml` |
| rustc | **1.99.0 (b940084d7 2026-09-28)** | preflight, MSRV satisfied |
| cargo | 1.99.0 | preflight |
| PyO3 (declared) | 0.29.2 | `[workspace.dependencies]` |
| PyO3 (locked) | **0.29.2** | `Cargo.lock` |
| pyo3-build-config (locked) | 0.29.2 | `Cargo.lock` |
| pyo3 features | `num-bigint` | `crates/jiter-python/Cargo.toml` |
| lib name / crate-type | `jiter_python`, `["cdylib", "rlib"]` | `[lib]` |
| requires-python | `>=3.10` | `[project]` |
| 3.14 classifier | present | `[project]` |

VERIFIED FROM SOURCE.

`crates/jiter` depends only on `num-bigint`, `num-traits`, `ahash`,
`smallvec`, `pyo3`, `lexical-parse-float`, `bitvec` and optional `serde`. All
are Rust crates. There is no `build.rs` that vendors C, no OpenSSL, no libffi,
no libsodium, and no external native library of any kind.

## 5. Build environment preflight

Hard check before building. All items passed:

```
HOST_PYTHON_314_OK           3.14.0
CPYTHON_ANDROID_DEV_TREE_OK  #define ANDROID_API_LEVEL 24
                             host contamination (python3.12 refs) = 0
LIBPYTHON_314_OK             4b99ed3aad65121db6c4c1830fba0592c0a9884a7f9a778165b6a34324059d83
RUST_OK                      rustc 1.99.0 (b940084d7 2026-09-28)
CARGO_OK                     cargo 1.99.0
NDK_OK                       Pkg.Revision = 27.2.12479018 (r27c)
TARGET_TRIPLE_OK             aarch64-linux-android
MATURIN_OK                   maturin 1.15.0
```

The Chaquopy runtime library used for linking is the real one extracted from
the shipped APK, sha256 `4b99ed3a...`, not a stub and not a locally rebuilt
copy. VERIFIED BY BUILD.

## 6. Cross-compilation configuration

`PYO3_PRINT_CONFIG=1` was used first, which makes PyO3 print the resolved
configuration and halt. That produced direct evidence rather than an
assumption:

```
implementation=CPython
version=3.14
shared=true
target_abi=CPython-gil_enabled-3.14
lib_name=python3.14
lib_dir=/build/work/pydev/lib
pointer_width=64
```

`version=3.14` and `lib_dir` pointing at the Android development tree prove
the build consumed Android CPython 3.14 and not host Python 3.12. The build
then ran without `PYO3_PRINT_CONFIG`.

Environment used:

```
PYO3_CONFIG_FILE=/build/work/pyo3_config.txt
PYO3_CROSS_LIB_DIR=/build/work/pydev/lib
PYO3_CROSS_PYTHON_VERSION=3.14
CARGO_BUILD_TARGET=aarch64-linux-android
CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=$TC/aarch64-linux-android24-clang
CC_aarch64_linux_android=/build/work/linkwrap/aarch64-clang
CFLAGS_aarch64_linux_android=-fPIC -I$NDK sysroot/usr/include -D__ANDROID_API__=24 -I<pydev include>
```

VERIFIED BY BUILD.

No source patch was applied. jiter built unmodified.

## 7. Compiler and linker evidence

```
Compiling jiter v0.17.0 (/build/work/jt/crates/jiter)
Compiling jiter-python v0.17.0 (/build/work/jt/crates/jiter-python)
Finished `release` profile [optimized] target(s) in 12.10s
```

Build time 12.10 s. No `error`, no fallback, no host compiler invocation.

## 8. ELF analysis

```
libjiter_python.so
ELF 64-bit LSB shared object, ARM aarch64
Class:    ELF64
Machine:  AArch64

DT_NEEDED:
  libpython3.14.so
  libdl.so
  libc.so

SONAME: (none, normal for a Python extension)

undefined CPython symbol types
  150 FUNC
   32 OBJECT
    0 NOTYPE        <- the M0-008I failure mode is absent

exported Py*_/pyo3 symbols: 0

size    921 112 bytes
sha256  a98dd22cab35cc2ee651dfcaacadc862b90dab560cf0206ba8fbc9bd3e2afbc3
```

VERIFIED BY ELF.

The `0 NOTYPE` figure is the specific guard requested for this milestone. It
is what the M0-008L-G linker wrapper buys: linking against the real
`libpython3.14.so` rather than a stub makes every CPython reference carry a
correct `STT_FUNC` or `STT_OBJECT` type, which is what the Android linker
requires.

No OpenSSL, no libffi, no host Linux library.

## 9. Package integrity

Built from the official PyPI aarch64 wheel with **only** the native module
replaced. Everything else is verbatim: `jiter/__init__.py`,
`jiter/__init__.pyi` (the authoritative API stub, 2319 bytes),
`jiter/py.typed`, `METADATA`, `LICENSE`, and the SBOM.

```
jiter-0.17.0-cp314-cp314-android_24_arm64_v8a.whl
9 entries
Tag: cp314-cp314-android_24_arm64_v8a
RECORD covers all: True
stale entries: []
wheel sha256 ed85cb5f1274636c442e1871cf6166989f610011240658ffd205315e0c817eec
```

The module file is renamed from `jiter.cpython-314-aarch64-linux-gnu.so` to
`jiter.cpython-314-aarch64-linux-android.so` so that CPython's
`EXTENSION_SUFFIXES` matches it on Android, which is the same rule that had to
be applied to `cffi` and `httptools` in earlier milestones.

maturin renames the `jiter_python` cdylib to `jiter` when producing the wheel.
That was verified against the official wheel rather than assumed.

No version variable, module name or wrapper API was invented.

## 10. Artifact identity

```
native build SHA = APK embedded SHA = device extracted SHA
a98dd22cab35cc2ee651dfcaacadc862b90dab560cf0206ba8fbc9bd3e2afbc3
```

Device path:

```
/data/data/dev.vitngan.probe.chaquopy/files/chaquopy/AssetFinder/requirements/jiter/jiter.cpython-314-aarch64-linux-android.so
```

`UP-TO-DATE` was not accepted: `app/build` was deleted before packaging, and
the APK payload was re-read from `assets/chaquopy/requirements-common.imy`.

VERIFIED ON DEVICE.

## 11. Physical-device evidence

Device: Xiaomi Redmi 24069RA21C, Android 16, API 36, arm64-v8a,
`getconf PAGE_SIZE` = 4096.

```
PYTHON_RUNTIME_OK  3.14.0
JITER_IMPORT_OK    builtins
JITER_PARSE_OK     nested+unicode+numbers
JITER_CACHE_OK     observed=0 cleared=0
JITER_LOSSLESS_OK
JITER_NATIVE_OK    parse+cache+lossless+invalid_rejected
```

Instrumented suite: **6 tests, 0 failures** (`Finished 6 tests on
24069RA21C - 16`, `BUILD SUCCESSFUL`). The single-test rerun
`jiterNativeOperation` also reported `OK (1 test)`.

VERIFIED ON DEVICE.

## 12. Native operation evidence

The API was read from the shipped `__init__.pyi` and from the upstream Rust
source, not from memory. Operations exercised:

- **A/B/C/D, nested parse.** A document with a string, a boolean, an integer
  (42), a float (1.5), an array, a three-level nested object and a null.
  Every field type was asserted with `type(...) is int` / `is float`, so a
  stringified number would fail.
- **D, Unicode.** The nested string contains a Latin-1 supplement character
  and two CJK ideographs, inserted as raw UTF-8. Round-trip equality was
  asserted.
- **F, boundary case.** Empty containers `{}`, `[]`, `[[[]]]`, plus `null`,
  `0` and `-0.0`.
- **Native string cache.** `cache_clear()` and `cache_usage()` are both
  called from Rust. See section 14 for why the observed usage value is
  recorded rather than asserted.
- **LosslessFloat.** `float_mode="lossless-float"` returns a Rust-side type.
  Both `type(...).__name__ == "LosslessFloat"` and the recovered value
  `1.2345678901234567` were asserted. A Python shim could not satisfy this.
- **E, negative control.** Five malformed inputs were each required to raise:
  `{"a":`, `{"a" 1}`, `[1,2`, `not json`, `{"a":1,}`. All five raised.
  `catch_duplicate_keys=True` on `{"a":1,"a":2}` was also required to raise.

Why this is native execution rather than import: `LosslessFloat` is a Rust
struct returned by value, the exact 17-significant-digit float round trip is
computed by the Rust lexical parser, and the five malformed inputs were
rejected by the Rust parser with Rust-side error messages. None of that is
reachable from a Python wrapper.

## 13. Negative controls

Fail-loud behaviour is enforced at two levels: `jiter_probe.run()` raises on
every failure, and `WheelProbe.runJiter()` throws when `NATIVE_OPERATION_OK`
is absent from the result, so a probe that stopped early still fails the test.

This was exercised repeatedly. Three probe-side defects were caught because
the tests kept failing:

1. **A missing closing brace in the test payload.** The JSON under test was
   syntactically invalid. jiter rejected it with
   `ValueError: EOF while parsing an object at line 1 column 117`. The test
   data was wrong, not jiter.
2. **Escaping through the file-generation layer.** The first attempt
   double-escaped the Unicode escapes, producing a payload that did not
   terminate. Fixed by injecting a real `UNICODE` constant instead of relying
   on nested escape levels.
3. **`cache_usage()` returning 0.** See section 14.

In all three cases the assertion was examined before being changed. Two were
probe bugs and were fixed in the probe. The third was an unfounded assumption
of mine and is reported as such rather than quietly removed.

## 14. A corrected assumption, stated plainly

The probe originally asserted `cache_usage() > 0` after a large parse. It
returned 0.

Reading `crates/jiter/src/py_string_cache.rs` shows why:

```rust
/// The number of entries in the string caches no parse is using.
pub fn cache_usage() -> usize {
    string_cache_pool().iter().map(PyStringCache::usage).sum()
}
```

It sums occupied entries only across caches that have been **returned to the
pool**. That is an internal accounting detail. The public `.pyi` documents
the function as "Get the size of the string cache" without promising a
non-zero result.

The assertion was therefore **my error, not a jiter failure**: I asserted an
internal behaviour that the API contract does not guarantee. The probe now
asserts only what is guaranteed, namely that both functions are real native
entry points returning `int`, and that `cache_clear()` leaves the usage at
zero. The observed value `0` is recorded in the result dict rather than
hidden.

## 15. Pipeline generalisation

The requested comparison is `pydantic-core` (Rust/PyO3) versus `jiter`
(Rust/PyO3).

**Genuinely shared, unchanged across every PyO3 package:**

| Component | Evidence |
|---|---|
| build image `m008l/build-env:2` | reused, preflight 7/7 |
| host CPython 3.14.0 | reused |
| Android CPython 3.14 development tree | reused, `ANDROID_API_LEVEL 24`, zero contamination |
| real Chaquopy `libpython3.14.so` | reused, sha256 `4b99ed3a...` |
| `PYO3_CONFIG_FILE` + `PYO3_CROSS_LIB_DIR` | reused unchanged, confirmed by `PYO3_PRINT_CONFIG` |
| linker wrapper `-Wl,--no-as-needed -lpython3.14` | reused unchanged, yielded `0 NOTYPE` |
| `cargo build --target aarch64-linux-android` | reused |
| official-wheel-plus-replaced-native packaging | reused, RECORD regenerated |
| Android extension-suffix renaming rule | reused |
| artifact SHA chain build = APK = device | reused |

**Package-specific:**

| Package | Extra needed |
|---|---|
| `pydantic-core` | Rust toolchain only |
| `cryptography` | plus an OpenSSL 3.0.18 prefix and `openssl-sys` |
| `jiter` | **nothing** |

What this does and does not support:

- It supports the narrow claim that the PyO3 cross-configuration, the Android
  development tree, the linker wrapper and the packaging chain transfer
  unchanged across three different Rust/PyO3 packages.
- It does **not** support a claim of universal generalisation. Three packages
  is a small sample, and the packages that have not been tried
  (`pillow-heif`, `resvg-py`) carry third-party native libraries, which is a
  structurally different situation from all three of these.

## 16. First remaining blocker

**None for jiter.** It is classified **A. JITER NATIVE PATH PROVEN**.

The remaining untested Hermes native dependencies are:

| Package | Carries third-party native code | Expected difficulty |
|---|---|---|
| `watchfiles` | no (Rust, notify) | low |
| `PyNaCl` | yes (libsodium) | medium, analogue of the solved libffi case |
| `Pillow` | yes (image codecs) | medium |
| `pillow-heif` | yes (libheif) | high |
| `resvg-py` | yes (resvg) | high |

## 17. Evidence labels

- Hermes dependency chain, resolution date, jiter versions, crate
  dependencies: **VERIFIED FROM SOURCE**
- build environment, PyO3 printed configuration, cargo completion:
  **VERIFIED BY BUILD**
- ELF headers, `DT_NEEDED`, symbol typing: **VERIFIED BY ELF**
- device import, native operations, artifact hash, `cache_usage` observed
  value: **VERIFIED ON DEVICE**
- remaining-difficulty table: **INFERRED**
