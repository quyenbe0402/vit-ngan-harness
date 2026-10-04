> **HISTORICAL RECORD.**
> This document records the state and evidence at the time of its milestone.
> Its historical contents are preserved unchanged, including any conclusion
> later overtaken by newer evidence or by the architecture pivot. Do not read
> it as current architecture guidance.
> Current architecture decisions are governed by `docs/PIVOT-DECISION.md`.

# M0-008L-C cryptography 50.0.1 Android Proof

## Headline

**C. CRYPTOGRAPHY BUILD BLOCKED**

The build was attempted and stopped at the first concrete blocker. Not proven
impossible; blocked on one missing build input that is identified precisely.

---

## 1. Hermes dependency evidence

`cryptography==50.0.1; python_version >= '3.14'`, pinned in both
`[project].dependencies` and the `[tool.uv]` override at
`NousResearch/hermes-agent` @ `eaecc99c`, for CVE-2026-69247,
GHSA-m2h6-j472-rp4c, GHSA-jwv3-5hgf-82ww, CVE-2026-39892, CVE-2026-34073 and
GHSA-537c-gmf6-5ccf. Also a transitive requirement of `PyJWT[crypto]==2.13.0`.
VERIFIED FROM SOURCE.

## 2. Exact cryptography version

**50.0.1**, from the official PyPI sdist `cryptography-50.0.1.tar.gz`,
SHA-256 `5dd9bda1c12b4162f6ff568eeb5e0ff956c28d14406e875cfe8a63a2d414ff20`.
Unmodified; no patch was applied and no version was changed.
VERIFIED FROM SOURCE.

## 3. OpenSSL provenance and local verification

Artifact taken from the M0-008L-B provenance:

```
https://github.com/beeware/cpython-android-source-deps/releases/download/
  openssl-3.0.18-0/openssl-3.0.18-0-aarch64-linux-android.tar.gz
```

| Check | Result |
|---|---|
| size | 4.93 MB |
| **LOCAL HASH** (sha256, locally calculated - **not** an upstream checksum) | `2ab2d0caf1d32a77a7ddb84ef92ff5ef518b7b1754178e96649555b510edbade` |
| upstream checksum | **CHECKSUM NOT PROVIDED BY SOURCE** |
| `include/openssl/opensslv.h` | `OPENSSL_VERSION_TEXT "OpenSSL 3.0.18 30 Sep 2025"` |
| `OPENSSL_VERSION_MAJOR/MINOR/PATCH` | 3 / 0 / 18 |
| `lib/libcrypto_python.so` | AArch64, SONAME `libcrypto_python.so`, NEEDED `libm.so libdl.so libc.so` |
| `lib/libssl_python.so` | AArch64, SONAME `libssl_python.so`, NEEDED `libm.so libcrypto_python.so libdl.so libc.so` |

VERIFIED BY BUILD. The library names in the recovered tree are **already**
`libcrypto_python.so` / `libssl_python.so`, matching the Chaquopy APK and the
`DT_NEEDED` of Chaquopy's own published cryptography wheel. That is the key
enabling fact for the shared route.

No Termux, host, or other OpenSSL was used. `vendored` remained off.

## 4. Build configuration

- NDK r27c, `aarch64-linux-android24-clang`
- Rust 1.99.0, target `aarch64-linux-android`
- `OPENSSL_DIR=/openssl`, `OPENSSL_LIB_DIR=/openssl/lib`,
  `OPENSSL_INCLUDE_DIR=/openssl/include`
- `PYO3_CROSS=1`, `PYO3_CROSS_PYTHON_VERSION=3.14`,
  `PYO3_CROSS_PYTHON_IMPLEMENTATION=CPython`
- link flags: `-L /openssl/lib`, `-lssl_python`, `-lcrypto_python`,
  `-lpython3.14`, wrapped in `-Wl,--no-as-needed` ... `-Wl,--as-needed`
- plain-name symlinks `libcrypto.so` and `libssl.so` were created inside the
  OpenSSL `lib` directory so that `openssl-sys` resolves `-lcrypto` / `-lssl`,
  while the SONAME keeps `DT_NEEDED` correct

## 5. OpenSSL detection - confirmed consumed

The build log shows the OpenSSL tree being used:

```
"-I" "/openssl/include"
```

and the cc invocation targeting the Android triple:

```
/opt/android/ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang
  "-O3" "-DANDROID" "-ffunction-sections" "-fdata-sections" "-fPIC"
  "-I" "/openssl/include" "-I" "/usr/include/python3.12" ...
  "-DPy_LIMITED_API=0x030900f0"
```

So `openssl-sys` did consume the recovered tree. VERIFIED BY BUILD.

Note `-DPy_LIMITED_API=0x030900f0`: the workspace enables pyo3 `abi3`, so the
C shim targets the stable ABI from Python 3.9.

## 6. Build result

**FAILED.** First blocker, recorded verbatim:

```
/usr/include/python3.12/pyconfig.h:9:12: fatal error:
    'aarch64-linux-gnu/python3.12/pyconfig.h' file not found
    9 | #  include <aarch64-linux-gnu/python3.12/pyconfig.h>
error occurred in cc-rs: command did not execute successfully
```

An earlier blocker was cleared first: `ModuleNotFoundError: No module named
'cffi'`, which is a build dependency (`_cffi_src` uses it), installed on the
host. That was a missing tool, not a source problem.

## 7. Root cause of the blocker

The C shim compile is reaching for **host** Python 3.12 headers:

- `-I /usr/include/python3.12` is the Ubuntu system Python, not an Android one.
- Its `pyconfig.h` then includes `<aarch64-linux-gnu/python3.12/pyconfig.h>`,
  which does not exist because it is a host-Linux layout, not an Android one.

This happens because `PYO3_CROSS_LIB_DIR` was not supplied. With
`PYO3_CROSS_PYTHON_VERSION=3.14` alone, pyo3 knows which version it wants but
still discovers include paths from the host interpreter, which is Python 3.12
here.

The missing input is therefore an **Android arm64 CPython 3.14 development
tree**: an `include/python3.14/pyconfig.h` configured for Android, plus
`libpython3.14.so` for the target. That is exactly what CPython's
`Android/android.py` produces, and it is a different artifact from the OpenSSL
one already recovered.

VERIFIED BY BUILD.

## 8. What this is not

- Not an Android limitation.
- Not an OpenSSL problem. The OpenSSL tree was found, verified, and was being
  used correctly.
- Not a source patch requirement. No patch to cryptography was needed or made.
- Not proven impossible. One identified input is missing.

## 9. ELF analysis, package completeness, APK/device identity

**Not applicable.** No `.so` was produced, so there was nothing to inspect,
nothing to package, and no device test ran. These sections are deliberately
empty rather than speculative.

In particular: the requirement that `DT_NEEDED` name `libssl_python.so` and
`libcrypto_python.so` remains **unverified**. The plan for it is sound, given
that the recovered libraries already carry those SONAMEs and symlinks were
used, but a plan is not evidence.

## 10. Runtime OpenSSL identity

Not reached. No native operation ran.

## 11. Native operation and negative control

Not reached. The existing probe remains a working negative control for the
loader path: the stub-linked `pydantic-core` binary still fails to load, and the
pydantic-core probe still passes loudly. That control is unchanged by this
milestone.

## 12. Artifact hashes

| Artefact | Hash | Kind |
|---|---|---|
| `cryptography-50.0.1.tar.gz` | `5dd9bda1c12b4162f6ff568eeb5e0ff956c28d14406e875cfe8a63a2d414ff20` | upstream |
| `openssl-3.0.18-0-aarch64-linux-android.tar.gz` | `2ab2d0caf1d32a77a7ddb84ef92ff5ef518b7b1754178e96649555b510edbade` | **LOCAL HASH** |

No `.so`, wheel, APK, OpenSSL archive, keystore, token or credential is
committed.

## 13. Supply-chain note

The shared route remains the right approach. The OpenSSL in use is the same
build Chaquopy already ships, so no second OpenSSL would enter the process and
no additional licence or notice obligation arises. `vendored` stayed off.

## 14. Remaining risks

1. The missing Android CPython 3.14 dev tree may itself need a cross-build.
2. `abi3` means the extension links the stable ABI. Forcing
   `DT_NEEDED libpython3.14.so` worked for pydantic-core, but here the build
   also compiles C, and the abi3 path may behave differently.
3. Even after a successful build, `DT_NEEDED` must be verified rather than
   assumed.
4. Eight further native packages remain untested. `pillow-heif` and `resvg-py`
   need third-party native libraries and are likely the hardest.

## 15. Next step

The single next experiment, and nothing larger:

1. Obtain an Android arm64 CPython 3.14 development tree. CPython's
   `Android/android.py` produces one, and its packaging step already lists
   `include/python*` and `lib/libpython*`, so the same beeware dependency
   mechanism is the likely route.
2. Set `PYO3_CROSS_LIB_DIR` to it and rebuild, with the first blocker recorded
   if another appears.
3. Only after a `.so` exists, verify `DT_NEEDED` names `libssl_python.so` and
   `libcrypto_python.so`, package against the official wheel's Python files,
   clear `app/build/python`, use `--rerun-tasks`, and compare build, APK and
   device hashes.

Do not enable `vendored`. The shared route is not what failed here; a Python
development tree was.

## 16. What was not done

- Hermes was not imported, initialised or modified.
- No other native dependency was built or tested.
- No production runtime code was written.
- `vendored` was not enabled.
- No device installation was performed.
