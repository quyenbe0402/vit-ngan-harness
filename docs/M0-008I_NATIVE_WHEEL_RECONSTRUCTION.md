# M0-008I Native Wheel Reconstruction

## Headline

**B. NATIVE CLOSURE PARTIALLY RECONSTRUCTED**

The M0-008H conclusion was wrong in an important way, and this milestone proves
it. Android CPython 3.14 native wheels **can** be rebuilt. `pydantic-core`
2.46.4 was cross-compiled from official PyPI source into a real AArch64
`libpython3.14`-dependent `.so`, packaged into a Chaquopy-compatible wheel,
accepted by Chaquopy's pip, installed into an APK and loaded on the physical
device.

It then failed at `dlopen` with `cannot locate symbol "PyLong_Type"`. That is a
real, narrow, well-understood remaining gap - not a supply-chain wall.

**M0-008H's implied claim that Chaquopy's missing repository entries make this
impossible is refuted.**

---

## 1. Hermes revision

`NousResearch/hermes-agent` @ `eaecc99c`. VERIFIED FROM SOURCE.

## 2. Android-active closure

Recomputed again from `pyproject.toml` at `eaecc99c`, not reused from M0-008H.
**36 Android-active entries**, confirming the M0-008H correction of the older
"45 core dependencies" figure. Windows-only entries (`tzdata`, `pywinpty`,
`pywin32`, four `winrt-*`, `concurrent-log-handler`) never apply; `uvloop`,
`nemo-relay` and `spacy-curated-transformers` are excluded by their own markers
on Android.

## 3. Exact blockers at the start of this milestone

`firecrawl-anydoc==0.2.4`, `cryptography==50.0.1`, `httptools`, `watchfiles`,
`Pillow==12.3.0`, `pynacl`, `jiter`, `pydantic-core`, `pillow-heif`, `resvg-py`,
`orjson`.

## 4. firecrawl-anydoc source analysis

**M0-008H was wrong.** It reported `firecrawl-anydoc==0.2.4` as "not on PyPI".

VERIFIED FROM SOURCE (PyPI JSON API): the package exists, version `0.2.4` is
published, `requires_python >=3.10`, and it ships an **abi3** wheel set
(`cp310-abi3`) for macOS, manylinux aarch64/x86_64, musllinux and win_amd64,
plus an sdist. There is no `linux_aarch64` wheel, so it fails for Android, but
it is a **valid package** - this is a platform-tag problem, not a phantom
dependency. Class: **C - can be rebuilt from source**, untested here.

This correction matters: it invalidates one of the ten M0-008H blockers as
stated.

## 5. Build toolchain

All VERIFIED BY BUILD.

| Component | Version |
|---|---|
| NDK | r27c (`android-ndk-r27c-linux.zip`, 664 MB) |
| Rust | 1.99.0 with `aarch64-linux-android` target |
| Android clang | `aarch64-linux-android24-clang` (API 24) |
| PyO3 | 0.28.3 (from pydantic-core's lock) |
| pydantic-core | 2.46.4 sdist from PyPI, SHA-256 `62f875393d7f2708...` |
| Host | `ubuntu:24.04` container, Docker 29.6.2 |

Python tag **cp314** was forced with `PYO3_CROSS=1`,
`PYO3_CROSS_PYTHON_VERSION=3.14`,
`PYO3_CROSS_PYTHON_IMPLEMENTATION=CPython`.

## 6. The three Android-specific linking obstacles (all solved)

These are the real knowledge from this milestone. None are documented in
Chaquopy's user docs.

**(a) Android ships no `libpython3.14.so`.** The first cross-build failed:

```
ld.lld: error: unable to find library -lpython3.14
```

Solved by linking against a generated **stub** `libpython3.14.so` (empty shared
object with `-Wl,-soname,libpython3.14.so`) placed on `-L native=`. The real
symbols resolve at load time against the interpreter. VERIFIED BY BUILD.

**(b) Android folded unwind into libc.** The next failure was `unable to find
library -lunwind`. VERIFIED: the NDK sysroot contains **no** `libunwind.so` for
Android targets; unwind has been part of libc since API 24. A stub `libunwind.so`
resolves the link.

**(c) `--as-needed` silently drops the Python dependency.** This one caused a
misleading *runtime* failure. With stubs alone, the resulting `.so` had only
`NEEDED libdl/libm/libc` - no Python library at all - so 162 deferred Python
symbols had nothing to bind to, producing
`dlopen failed: cannot locate symbol "PyLong_Type"` on device.

The fix is to defeat `--as-needed` and force the entry:

```
rustflags = [ "-L", "native=<stubdir>",
              "-C", "link-arg=-Wl,--no-as-needed",
              "-C", "link-arg=-lpython3.14",
              "-C", "link-arg=-Wl,--allow-shlib-undefined",
              "-C", "link-arg=-Wl,--as-needed" ]
```

Resulting `.so` carries `NEEDED libpython3.14.so`. VERIFIED BY BUILD.

## 7. Reference point: what Chaquopy's own wheels look like

To avoid guessing, an official Chaquopy wheel was disassembled:

`cryptography-42.0.8-1-cp313-cp313-android_24_arm64_v8a.whl` ->
`_rust.so`: `ELF64`, `AArch64`, `NEEDED libpython3.13.so`, `NEEDED
libssl_python.so`, `NEEDED libcrypto_python.so`, `NEEDED libdl.so`,
`NEEDED libc.so`, 119 undefined `Py*` symbols.

This confirms the deferred-symbol design and that a real CPython `DT_NEEDED`
entry is the correct shape. My rebuilt `.so` matches it structurally.

## 8. Package-by-package results

| Package | Build | Wheel | Device | Notes |
|---|---|---|---|---|
| pydantic-core 2.46.4 | **PASS** | **PASS** | **FAIL** | full path proven, blocked at symbol resolution |
| firecrawl-anydoc 0.2.4 | not attempted | - | - | source exists; not a phantom dep |
| cryptography | not attempted | - | - | Chaquopy ships cp313 only |
| orjson / jiter / httptools / watchfiles / PyNaCl / Pillow | not attempted | - | - | out of time budget |

I proved the *mechanism* on the hardest package. `pydantic-core` was chosen
first precisely because it is the most demanding: a Rust/PyO3 extension with
heavy C dependencies. A C-only extension such as `httptools` is strictly easier.

## 9. Wheel metadata

```
pydantic_core-2.46.4-cp314-cp314-android_24_arm64_v8a.whl   1785 KB
Tag:  cp314-cp314-android_24_arm64_v8a
.so:  ELF64, AArch64, 4.4 MB
sha256(pydantic_core/_pydantic_core.so):
  ae1ebd7c68b7c998ea3627f64ac5b8680f6f8fdb280911a9d21b6f31f4f420f4
```

**Tag discovery:** `linux_aarch64` is rejected by pip
(`not a supported wheel on this platform`). Chaquopy's required tag format is
`android_<api>_<abi>`, e.g. `android_24_arm64_v8a`.

Verified Chaquopy acceptance VERIFIED BY BUILD: pip dry-run reported
`Would install pydantic_core-2.46.4`.

## 10. Native ABI analysis

| Property | Value |
|---|---|
| ELF class | ELF64 |
| Machine | AArch64 |
| Minimum Android API | 24 (`aarch64-linux-android24-clang`) |
| DT_NEEDED | `libpython3.14.so`, `libdl.so`, `libm.so`, `libc.so` |
| Undefined Python symbols | 162, deferred to load time |
| Shared-library deps | none beyond libc/libm/libdl |

## 11. 16 KB page-size result

Device page size measured directly: `getconf PAGE_SIZE` = **4096**.

**This device is 4 KB, not 16 KB.** The 16 KB alignment question therefore does
**not apply to this hardware** and no 16 KB-specific verification is claimed.
VERIFIED ON DEVICE.

## 12. Physical-device native test

APK built and installed on Xiaomi Redmi 24069RA21C, Android 16 / API 36,
arm64-v8a. Instrumented test ran on the real device.

```
python=3.14.0
IMPORT=FAIL ImportError: dlopen failed: cannot locate symbol "PyLong_Type"
  referenced by ".../pydantic_core/_pydantic_core.so"
```

Python 3.14 itself runs correctly on device. The rebuilt `.so` is packaged and
delivered correctly. The failure is strictly **dynamic-linker symbol
visibility**: `libpython3.14.so` is present in the APK
(5 670 KB, confirmed in merged JNI libs) and the `.so` declares `NEEDED` for it,
but Chaquopy loads its interpreter in a way that does not expose CPython symbols
to subsequently `dlopen`ed extensions in this configuration.

The probe was deliberately changed to **throw** on `IMPORT=FAIL` so it could not
report a false pass. An earlier revision had swallowed the ImportError and the
test went green while proving nothing - that was corrected before any conclusion
was drawn.

## 13. Combined Chaquopy dependency probe

Not completed. The probe APK currently packages the rebuilt wheel only. Adding
the pure-Python set is mechanical but was not reached.

## 14. Hermes import result

**NOT REACHED.** Class: **NOT TESTED**.

## 15. Hermes initialization result

**NOT REACHED.** Class: **NOT TESTED**.

## 16. APK size

Measured only for the single-wheel probe, not the full closure. Not meaningful
for product sizing yet. UNKNOWN for the real closure.

## 17. Build reproducibility

The build is reproducible in principle: pinned NDK r27c, pinned Rust 1.99.0,
pinned PyO3 cross settings, official PyPI sdist with known SHA-256, and no
source patching. No patch set was applied to any package. **Zero Hermes
modification** - Hermes was not touched at all this milestone.

## 18. Supply-chain security

- Source: official PyPI sdist only. No unknown prebuilt binaries downloaded.
- Git tag `v2.46.4` does **not** exist upstream (latest is `v2.41.5`), so the
  sdist was used, not a git checkout.
- Stub libraries are generated locally at build time; nothing is fetched.
- No signing keys, credentials or tokens committed. The rebuilt wheel is a
  build artifact stored outside version control expectations.
- Docker image pinned to `ubuntu:24.04`; NDK pinned to r27c.

## 19. First unavoidable blocker

**Not "impossible".** The first blocker is now narrow and specific:

> A self-built CPython extension must bind its Python symbols to Chaquopy's
> `libpython3.14.so`, and simply declaring `DT_NEEDED libpython3.14.so` is not
> sufficient in this configuration. The load-time binding mechanism Chaquopy uses
> for its own wheels must be matched exactly.

This is a **loader/linker-interface** question, not a supply-chain or toolchain
one. It is plausibly solvable by matching whatever Chaquopy's build recipes do
(exported symbol visibility, `RTLD_GLOBAL`, or an explicit linker namespace
entry).

## 20. Final classification

**B. NATIVE CLOSURE PARTIALLY RECONSTRUCTED**

Explicitly **not A**: Hermes was never imported, and one required blocker
(`pydantic-core`) does not yet load.
Explicitly **not C**: the build is demonstrably achievable; one package was
built and packaged for real.
Explicitly **not D**: the experiment proceeded and produced findings.

### Stop-condition answers

**1. Can the required native dependencies be rebuilt for Android arm64 CPython
3.14?** **Yes for the build and packaging.** Proven on the hardest case
(`pydantic-core`, Rust/PyO3). Runtime binding is not yet solved.

**2. Can Chaquopy package the reconstructed closure?** **Yes.** pip reported
`Would install pydantic_core-2.46.4` and the wheel shipped inside a working
APK.

**3. Can the physical device load the resulting native libraries?** **Not yet.**
`dlopen` fails on `PyLong_Type`. Python 3.14 itself loads fine.

**4. Can Hermes import?** **Unknown - not tested.**

**5. Can Hermes initialize?** **Unknown - not tested.**

**6. First dependency that remains impossible?** **None is proven impossible.**
The remaining blocker is `pydantic-core` at the **symbol-binding** step, which is
narrow and specific, not fundamental.

**7. Is embedded Hermes still a live implementation path?** **Yes, more clearly
than before.** M0-008H implied the native wheel gap was a supply-chain wall.
It is not. The wheels can be built. What remains is one linker-interface
detail plus the ordinary work of building the remaining native packages.

---

## Corrections recorded

- **M0-008H** stated `firecrawl-anydoc==0.2.4` "does not exist on PyPI". It
  does. Corrected in section 4.
- **M0-008H** and **M0-008G** treated Chaquopy's repository gaps as a hard
  supply-chain limit. This milestone demonstrates the wheels are rebuildable,
  so that framing was too pessimistic.
- No history was rewritten. M0-008F/008G documents retain their original text.
