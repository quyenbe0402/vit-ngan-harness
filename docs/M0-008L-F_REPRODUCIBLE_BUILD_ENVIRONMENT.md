> **HISTORICAL RECORD.**
> This document records the state and evidence at the time of its milestone.
> Its historical contents are preserved unchanged, including any conclusion
> later overtaken by newer evidence or by the architecture pivot. Do not read
> it as current architecture guidance.
> Current architecture decisions are governed by `docs/PIVOT-DECISION.md`.

# M0-008L-F Reproducible Android Build Environment

## Headline

**B. DEV TREE PROVEN + CRYPTOGRAPHY BLOCKER IDENTIFIED**

The build environment is now persistent and reproducible. The Android CPython
3.14 development tree is generated and verified free of host contamination. The
cryptography 50.0.1 native extension **compiles and links to a valid Android
AArch64 ELF** whose `DT_NEEDED` matches Chaquopy's own cryptography wheel
exactly. Packaging then stops on a new, concrete, unrelated blocker: `cffi`.

This milestone did not reach the device. No device claim is made.

---

## 1. Why M0-008L-E failed

M0-008L-E provisioned host CPython 3.14.0 correctly, then reported:

```
configure: error: invalid or missing build python binary "/opt/python314/bin/python3"
```

Root cause: **not a technical problem.** The interpreter was extracted into the
writable layer of a container started with `docker run --rm`. `--rm` deletes the
container filesystem on exit, so the next `docker run --rm` saw an empty
`/opt/python314`. The "missing build python" was a missing *container*, not a
missing *interpreter*.

The same `--rm` pattern had already silently deleted the 1.9 GB NDK and the
OpenSSL tree between steps in earlier milestones.

**Design rule adopted:** every toolchain input is baked into one immutable
image; nothing that later stages depend on may live in an `--rm` container's
writable layer.

---

## 2. Build-image design

`probes/build-env/Dockerfile` -> image `m008l/build-env:2`.

Everything a later stage needs is baked into the image or read-only mounted:

| Input | Where it lives | Lifetime |
|---|---|---|
| host CPython 3.14.0 | `/opt/python314` (image layer) | image |
| Rust 1.99.0 + Android std | `/opt/cargo`, `/opt/rustup` (image layer) | image |
| Android NDK r27c | named volume `ndkcache` -> `/opt/android` (ro) | persistent |
| OpenSSL 3.0.18 | `/openssl` (image layer) | image |
| CPython 3.14.0 source | `/build/cpython-src` (image layer) | image |
| working tree | `/build/work` (container writable layer, long-lived container) | container |

The build container is started **without** `--rm` (`docker run -d ... sleep
infinity`) and driven by `docker exec`. That is the specific change that fixes
L-E.

---

## 3. Host Python provenance

```
astral-sh/python-build-standalone, release tag 20251014
asset cpython-3.14.0+20251014-x86_64-unknown-linux-gnu-install_only.tar.gz
SHA-256 74d4516a64abc63ae4bcbffb35482879a85b7faa187fcfa47c1ca8f00faebf5f
```
VERIFIED BY BUILD.

Inside the image:

```
Python 3.14.0  cpython  x86_64
```

The newest release of that repository carries only 3.14.8. The milestone pins
**3.14.0**, so the older release tag is used. Substituting 3.14.8 would have
silently changed the build interpreter.

`--enable-shared` is **not** set by default; see section 5.

---

## 4. Toolchain versions (all pinned)

| Component | Version | Source |
|---|---|---|
| base image | `ubuntu:24.04` | Docker Hub |
| host CPython | 3.14.0 | python-build-standalone 20251014 |
| Rust | 1.99.0 (b940084d7 2026-09-28) | rustup, `--default-toolchain 1.99.0` |
| Rust target | `aarch64-linux-android` | `rustup target add` |
| Android NDK | r27c / 27.2.12479018 | volume `ndkcache` |
| OpenSSL | 3.0.18 | beeware/cpython-android-source-deps, tag `openssl-3.0.18-0` |
| CPython source | 3.14.0 | python.org tarball |
| pyo3 / pyo3-ffi / pyo3-build-config | 0.29.0 | cryptography 50.0.1 `Cargo.lock` |
| openssl-sys | 0.9.117 | cryptography 50.0.1 `Cargo.lock` |

CPython 3.14.0 tarball SHA-256 (LOCALLY computed; python.org publishes no
checksum on that URL):

```
88d2da4eed42fa9a5f42ff58a8bc8988881bd6c547e297e46682c2687638a851  Python-3.14.0.tgz
```

OpenSSL 3.0.18 artifact SHA-256 (LOCALLY computed; upstream publishes none —
CPython's `download()` is a bare `curl -Lf --retry 5`):

```
2ab2d0caf1d32a77a7ddb84ef92ff5ef518b7b1754178e96649555b510edbade
```

Preflight inside the final image:

```
HOST_PYTHON_OK: 3.14.0 cpython
NDK_OK: Pkg.Revision = 27.2.12479018
RUST_OK: rustc 1.99.0 (b940084d7 2026-09-28)
OPENSSL_TREE_OK: # define OPENSSL_VERSION_TEXT "OpenSSL 3.0.18 30 Sep 2025"
CPYTHON_SOURCE_OK: #define PY_VERSION "3.14.0"
WORKSPACE_PERSISTENT_OK: /build/work
```

VERIFIED BY BUILD.

---

## 5. CPython Android configure, and the `--enable-shared` correction

Invocation (no invented flags; every option below is a real CPython option):

```
./configure \
  --host=aarch64-unknown-linux-android --build=x86_64-pc-linux-gnu \
  --with-build-python=/opt/python314/bin/python3 --without-ensurepip \
  --enable-shared \
  ac_cv_file__dev_ptmx=no \
  CC=$TC/aarch64-linux-android24-clang CXX=$TC/aarch64-linux-android24-clang++ \
  LDSHARED="... aarch64-linux-android24-clang -shared" \
  AR=$TC/llvm-ar RANLIB=$TC/llvm-ranlib STRIP=$TC/llvm-strip \
  CFLAGS="-fPIC -I$SYSROOT/usr/include"
```

Target resolved as `aarch64-unknown-linux-android` (the Android `config.guess`
path is active). VERIFIED BY BUILD.

**Why `--enable-shared` is mandatory.** Without it CPython configures a static
interpreter and its generated `_sysconfigdata` says:

```
'Py_ENABLE_SHARED': 0,  'LDLIBRARY': 'libpython3.14.a',  'INSTSONAME': 'libpython3.14.a'
```

pyo3-ffi reads that file (`pyo3-build-config-0.29.0/src/impl_.rs:594`) and then
emits `cargo:rustc-link-lib=static=python3.14` (`pyo3-ffi-0.29.0/build.rs:213`).
Result:

```
error: could not find native static library `python3.14`, perhaps an -L flag is missing?
```

Chaquopy ships a **shared** interpreter — the APK contains
`lib/arm64-v8a/libpython3.14.so` with SONAME `libpython3.14.so`. So the target
tree must be configured shared too:

```
'Py_ENABLE_SHARED': 1,  'LDLIBRARY': 'libpython3.14.so',  'INSTSONAME': 'libpython3.14.so'
'SOABI': 'cpython-314-aarch64-linux-android',  'MULTIARCH': 'aarch64-linux-android'
```

Reconfiguring with `--enable-shared` fixes it. VERIFIED BY BUILD.

### pyo3 cross configuration

`PYO3_CONFIG_FILE` (documented `pyo3-build-config` mechanism) is used because
pyo3 otherwise derives the static/shared decision from the sysconfigdata file it
finds, and re-derivation proved fragile across configure changes:

```
implementation=CPython
version=3.14
shared=true
lib_name=python3.14
lib_dir=/build/work/pydev/lib
pointer_width=64
build_flags=
```

Note the format is `key=value` with **no spaces** — `from_reader` splits on
`=` and matches the key verbatim without trimming.

Also required:

```
PYO3_CROSS_LIB_DIR=/build/work/pydev/lib
PYO3_CROSS_PYTHON_VERSION=3.14
CARGO_BUILD_TARGET=aarch64-linux-android
CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=$TC/aarch64-linux-android24-clang
CC_aarch64_linux_android / AR_aarch64_linux_android / CFLAGS_aarch64_linux_android
```

`libpython3.14.so` in that directory is **the actual Chaquopy 17.0 runtime
library extracted from the probe APK**, not a stub and not a locally rebuilt
copy:

```
sha256 4b99ed3aad65121db6c4c1830fba0592c0a9884a7f9a778165b6a34324059d83
```

This guarantees the extension links against the exact ABI that will be present
at runtime.

---

## 6. Generated development tree

`/build/work/pydev/include` = `pyconfig.h` + `Include/` from the configured
Android source tree.
`/build/work/pydev/lib` = Chaquopy's real `libpython3.14.so`, the generated
`_sysconfigdata__android_aarch64-linux-android.py`, and the OpenSSL runtime
libraries.

`pyconfig.h` verification:

```
#define ANDROID_API_LEVEL 24
grep -c python3.12          -> 0
grep -c '"/usr/include'      -> 0
```

**No reference to `/usr/include/python3.12` or any host Linux Python path.**
This is the exact condition M0-008L-C and M0-008L-D were blocked on.

VERIFIED BY BUILD.

A full `make` of CPython does not complete in this environment
(`Modules/_lzmamodule.o` fails: the Android NDK sysroot has no `lzma.h`). That
is irrelevant to the question here — the required outputs, `pyconfig.h` and
`_sysconfigdata`, are both produced before that failure, and the actual
`libpython3.14.so` used is Chaquopy's, not a locally built one.

---

## 7. OpenSSL prefix

`openssl-sys` requires a conventional prefix (`include/openssl` + `lib`).
Merging the Python headers and the OpenSSL headers into one `include/` — an
early mistake in this milestone — produced:

```
build/expando.c:1:10: fatal error: 'openssl/opensslv.h' file not found
```

Fixed by giving OpenSSL its own prefix `/build/work/ossl`:

```
include/openssl/opensslv.h     -> OPENSSL_VERSION_TEXT "OpenSSL 3.0.18 30 Sep 2025"
lib/libcrypto_python.so        (SONAME libcrypto_python.so)
lib/libssl_python.so           (SONAME libssl_python.so)
lib/pkgconfig/{libcrypto,libssl,openssl}.pc
```

The libraries in the Chaquopy/CPython OpenSSL artifact are **already named**
`libcrypto_python.so` / `libssl_python.so`. That is what makes the shared route
possible without bundling a second OpenSSL. No `vendored` feature was enabled at
any point in this milestone.

---

## 8. cryptography 50.0.1 build result

Source: official sdist `cryptography-50.0.1.tar.gz`,
SHA-256 `5dd9bda1c12b4162f6ff568eeb5e0ff956c28d14406e875cfe8a63a2d414ff20`.
Unmodified.

Build command reaches a **valid Android native library**:

```
cargo build --release --target aarch64-linux-android -p cryptography-rust
...
Finished `release` profile [optimized] target(s) in 21.06s
```

Output: `/build/work/crypto/target/aarch64-linux-android/release/libcryptography_rust.so`

VERIFIED BY BUILD.

---

## 9. ELF analysis — ACCEPTED

```
ELF 64-bit LSB shared object, ARM aarch64
Class:    ELF64
Machine:  AArch64
```

`DT_NEEDED`:

```
libpython3.14.so
libssl_python.so
libcrypto_python.so
libdl.so
libc.so
```

Compare the real Chaquopy cryptography wheel
(`cryptography-42.0.8-1-cp313-cp313-android_24_arm64_v8a.whl`), disassembled in
M0-008L:

```
libpython3.13.so, libssl_python.so, libcrypto_python.so, libdl.so, libc.so
```

**Structurally identical apart from the Python minor version.** The shared
OpenSSL route produces exactly the linkage Chaquopy itself produces.

Undefined symbols:

```
Py*   : 302
OpenSSL (EVP_/SSL_/X509_/OPENSSL_/CRYPTO_) : 732
```

Symbol typing is correct — the failure mode found in M0-008I (`STT_NOTYPE`
because a stub was linked instead of a real `libpython`) does not occur:

```
UND PyLong_Type                 OBJECT
UND SSL_CTX_new@OPENSSL_3.0.0    FUNC
UND EVP_EncryptInit_ex@...       FUNC
```

The `OPENSSL_3.0.0` symbol version proves the OpenSSL 3.0.18 headers were used.

Binary:

```
size   6 701 272 bytes
sha256 137b70f4daae4c34d247361b5157ab4ec85b92c0d69e0ac9832d58acbd14b8c3
```

VERIFIED BY ELF.

---

## 10. Package completeness

The wheel is built by taking the **official** PyPI wheel
`cryptography-50.0.1-cp311-abi3-manylinux2014_aarch64.manylinux_2_17_aarch64.whl`
(SHA-256 `53e279950892dc102c6b4e52af03ae5ea92fac572a1ddab78ca73a997f62b69f`,
141 entries) and replacing **only** `cryptography/hazmat/bindings/_rust.abi3.so`.
Every other entry — Python modules, `.pyi` stubs, `METADATA`, licences — is the
official file verbatim. `RECORD` hashes are recomputed for the changed binary
and the `WHEEL` tag is rewritten.

This is deliberately the method that fixed the M0-008K-FIX failure, where a
hand-reconstructed package was missing `core_schema.py`.

```
cryptography-50.0.1-cp37-abi3-android_24_arm64_v8a.whl
120 entries, 2 029 064 bytes
sha256 b6f0c119d125828b62c6c0242c63958b14c85e6edaa3990ba00e4abbd29843aa
native: cryptography/hazmat/bindings/_rust.abi3.so (6 701 272 bytes)
```

VERIFIED BY BUILD.

---

## 11. Physical-device result

**NOT REACHED.** No APK containing this wheel was installed. No
`PYTHON_RUNTIME_OK`, `CRYPTOGRAPHY_IMPORT_OK`, `OPENSSL_RUNTIME_OK` or
`NATIVE_OPERATION_OK` is claimed.

Chaquopy packaging fails before APK assembly:

```
Processing .../cryptography-50.0.1-cp37-abi3-android_24_arm64_v8a.whl
ERROR: Could not find a version that satisfies the requirement cffi>=2.0.0;
       platform_python_implementation != "PyPy" (from cryptography)
ERROR: No matching distribution found for cffi>=2.0.0
```

The probe code (`probes/chaquopy-closure/app/src/main/python/crypto_probe.py`)
and the instrumentation test are written and fail-loud by construction, but
they have not executed.

---

## 12. FIRST NEW BLOCKER — `cffi` has no Android cp314 wheel

`cryptography==50.0.1` declares:

```
Requires-Dist: cffi>=2.0.0 ; platform_python_implementation != 'PyPy'
```

VERIFIED FROM SOURCE.

Chaquopy's own repository has `cffi` wheels for `cp38`, `cp39`, `cp310`, `cp311`,
`cp312` and `cp313`, with **no cp314 build** — the same cp313 ceiling that
M0-008H found across every other native package.

Chaquopy's own `cryptography` wheel (42.0.8) also stops at `cp313`.

VERIFIED FROM SOURCE.

Attempting to rebuild `cffi` 2.1.1 from the official sdist (SHA-256
`dd31f52ea1086513bb9df30f8fcee9b8918323ae067a3d5b78bc826a000712be`) with the
same cross toolchain and the Android development tree from section 6:

```
src/c/_cffi_backend.c:15:10: fatal error: 'ffi.h' file not found
```

`libffi` headers are absent from the NDK sysroot
(`find /opt/android/ndk -name ffi.h` returns nothing) and absent from the
Chaquopy OpenSSL artifact. VERIFIED BY BUILD.

### Why this blocker is real and not cosmetic

`cffi` is a **runtime** requirement in `METADATA`, so pip refuses to install
cryptography without it. However, inspection of the official 50.0.1 wheel shows
**no Python module imports `cffi`** — the Rust binding uses it only at build
time via `cryptography-cffi`'s build script.

This means the blocker is *not* "cryptography needs cffi at runtime". It is
"cryptography's declared dependency set cannot be satisfied on Android cp314".
Two ways out exist and **neither is chosen here**:

1. rebuild `cffi` for Android arm64 cp314, which requires an Android `libffi`
   build (headers plus a shared library whose SONAME the loader can find);
2. install with `--no-deps`, which changes the dependency contract and is a
   product/security decision, not a build-plumbing one.

Both are out of scope for this milestone. Recording, not deciding.

---

## 13. Supply-chain and security notes

| Item | Status |
|---|---|
| host CPython | pinned URL + pinned SHA-256, baked into image |
| CPython source | pinned URL + locally computed SHA-256 (no upstream checksum) |
| OpenSSL artifact | pinned URL + locally computed SHA-256 (no upstream checksum) |
| cryptography | official PyPI sdist, unmodified, SHA-256 recorded |
| cffi | official PyPI sdist, unmodified, SHA-256 recorded |
| Rust / NDK | pinned versions |
| upstream checksums | **NOT PROVIDED BY SOURCE** for CPython and OpenSSL |
| `vendored` OpenSSL | never enabled |
| Termux / root / Shizuku | not used |
| binaries committed | none; wheels, `.so` and APK remain gitignored |

Two observations worth carrying forward, both **INFERRED** from source:

- neither Chaquopy's `build.sh` nor CPython's `download()` verifies a checksum;
  build-input integrity rests on release tag immutability;
- the OpenSSL artifact's libraries are pre-named `lib*_python.so`, which is what
  makes the shared-OpenSSL route possible at all.

No legal conclusion is drawn about OpenSSL licensing. The shared route was used
and no second OpenSSL is bundled; if that changes, PRODUCT/LEGAL REVIEW REQUIRED.

---

## 14. What is proven, and what is not

PROVEN:
- build environment is persistent and reproducible (`m008l/build-env:2`);
- host CPython 3.14.0 is available in every build stage;
- CPython Android `configure` succeeds for `aarch64-unknown-linux-android`;
- Android CPython 3.14 development tree is generated and contains zero host
  contamination;
- `cryptography` 50.0.1 compiles and links to a valid Android AArch64 ELF;
- that ELF's `DT_NEEDED` matches Chaquopy's own cryptography wheel;
- OpenSSL symbols are resolved against the Chaquopy 3.0.18 runtime;
- a Chaquopy-packaged Android wheel can be constructed from official contents.

NOT PROVEN:
- that the wheel installs into a Chaquopy app (blocked on `cffi`);
- anything at all on the physical device.

---

## 15. Exact next step

One package, one experiment:

> Rebuild `cffi` 2.1.1 for Android arm64 / cp314 using this same image and
> development tree, supplying an Android `libffi` (headers plus a shared library
> with an Android-resolvable SONAME). Then re-run the Chaquopy packaging step and,
> only if the APK builds, run `crypto_probe.run()` on the physical device.

If `cffi` proves to require a large native dependency of its own, that result
should be weighed against Candidate D (remote Hermes runtime) before any further
native packages are attempted. Eight native dependencies remain untouched:
`orjson`, `jiter`, `Pillow`, `PyNaCl`, `httptools`, `watchfiles`, `pillow-heif`,
`resvg-py` — and `pillow-heif` / `resvg-py` are expected to be harder than
`cryptography` because they carry third-party native libraries.

---

## 16. Classification

**B. DEV TREE PROVEN + CRYPTOGRAPHY BLOCKER IDENTIFIED**

The two objectives this milestone set out to remove — the non-reproducible build
environment and the host-Python-contaminated development tree — are both removed
and verified. cryptography 50.0.1 reaches a valid Android AArch64 native library
with the correct Chaquopy linkage. The remaining blocker is `cffi`, a
transitive dependency of the same package, and it is a supply-chain gap rather
than a platform limitation.
