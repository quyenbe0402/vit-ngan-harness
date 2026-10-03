# M0-008L-G libffi -> cffi -> cryptography

## Headline

**A. CFFI + CRYPTOGRAPHY NATIVE PATH PROVEN**

The chain that M0-008L-F stopped on is now complete, on the physical device.

libffi 3.4.4 (Android aarch64, Chaquopy's own artifact)
  -> cffi 2.1.1 (cp314, Android arm64)
  -> cryptography 50.0.1 (Rust + Chaquopy OpenSSL 3.0.18)

All three native libraries load and execute on the Xiaomi Redmi 24069RA21C
(Android 16 / API 36 / arm64-v8a). Cryptographic operations really ran: Ed25519
sign/verify, AES-GCM encrypt/decrypt with authentication rejection, and X.509
certificate signing and re-parsing.

---

## 0. Git synchronisation status

This is reported separately because the previous reports claimed pushes that
had not happened.

```
$ git remote -v
origin  https://github.com/quyenbe0402/vit-ngan-harness.git

$ git branch -r          # remote heads
origin/cline/M0-007b-termux-spike      <- newest remote branch
```

Verified per branch with `git ls-remote --heads origin <branch>`:

| Milestone | Branch | Remote | Local commit |
|---|---|---|---|
| M0-008F | cline/M0-008f-termux-plugin-proof | REMOTE | 2368b32 |
| M0-008G | cline/M0-008g-runtime-strategy-pivot | REMOTE | 0a04195 |
| M0-008H | cline/M0-008h-chaquopy-closure | REMOTE | d5e0ee3 |
| M0-008I | cline/M0-008i-native-wheel-rebuild | REMOTE | 219f3a0 |
| M0-008J | cline/M0-008j-chaquopy-native-loader | REMOTE | 8d89492 |
| M0-008K | cline/M0-008k-system-load-vs-dlopen | REMOTE | f1fdb17 |
| M0-008K-FIX | cline/M0-008k-fix-pydantic-operation | REMOTE | 4e2f73f |
| M0-008L | cline/M0-008l-cryptography-android | REMOTE | 555c2f9 |
| M0-008L-A | cline/M0-008l-a-openssl-build-interface | REMOTE | e57c208 |
| M0-008L-B | cline/M0-008l-b-cpython-openssl-provenance | REMOTE | f251de0 |
| M0-008L-C | cline/M0-008l-c-cryptography-proof | REMOTE | 7535b5f |
| M0-008L-D | cline/M0-008l-d-cpython314-dev-tree | REMOTE | 8317d90 |
| M0-008L-E | cline/M0-008l-e-host-python-314 | REMOTE | ec773de |
| M0-008L-F | cline/M0-008l-f-reproducible-build | **LOCAL ONLY** | c1dbe1f |
| M0-008L-G | cline/M0-008l-g-libffi-cffi | **LOCAL ONLY** | this commit |

Correction to the M0-008L-F report: M0-008F through M0-008L-E **were** pushed.
The earlier `git branch -r` listing was read before `git fetch`, which made
14 branches look local-only. The one genuinely unpushed branch is M0-008L-F.

### Push failure for this milestone

`git push` for M0-008L-G fails:

```
remote: Permission to quyenbe0402/vit-ngan-harness.git denied to quyenbe0402.
fatal: unable to access '...': The requested URL returned error: 403
```

The GitHub API reports `permissions.push = true` for the same token, but git
over HTTPS is rejected. Auth was attempted with a header, with
`GIT_ASKPASS`, and with an explicit `credential.helper=` override; all produce
403 or an authentication failure. No credential was embedded in the remote URL.

This is an environment/authentication problem, not a repository problem, and
not something this probe can fix. History was not rewritten and no force-push
was attempted. The commit exists locally only.

---

## 1. Exact cryptography requirement

From PyPI metadata for `cryptography==50.0.1`, re-read in this milestone:

```
Requires-Dist: cffi>=2.0.0 ; platform_python_implementation != "PyPy"
Requires-Dist: typing-extensions>=4.13.2 ; python_full_version < "3.11"
Requires-Dist: bcrypt>=3.1.5 ; extra == 'ssh'
```

VERIFIED FROM SOURCE.

The Hermes `@ eaecc99c` pin remains `cryptography==50.0.1`, reached
transitively through `PyJWT[crypto]==2.13.0`.

`typing-extensions` is gated on `python_full_version < "3.11"` and does not
apply on 3.14. `bcrypt` is an extra that Hermes does not request. So `cffi` is
the only new requirement in this milestone.

## 2. Exact cffi requirement

`cffi>=2.0.0`. The resolver selects the newest release, **cffi 2.1.1**.

```
cffi 2.1.1 metadata:
Requires-Dist: pycparser ; implementation_name != "PyPy"
```

VERIFIED FROM SOURCE.

`pycparser` 3.0 ships only `pycparser-3.0-py3-none-any.whl` and its sdist — it
is pure Python, so it resolves on Android without reconstruction. It is not in
Chaquopy's index, which does not matter for a `py3-none-any` wheel.

## 3. cffi build system

```
build-backend = setuptools.build_meta
requires-python = >=3.10
sources = ["src/c/_cffi_backend.c"]
libraries = ["ffi"]
define_macros = [("FFI_BUILDING", "1")]   # for linking with libffi static library
```

VERIFIED FROM SOURCE.

The real build contract, read from `setup.py`:

- `_ask_pkg_config(include_dirs, '--cflags-only-I', '-I')`
- `_ask_pkg_config(libraries, '--libs-only-l', '-l')`

both invoking `pkg-config ... libffi`. Fallback include paths are
`/usr/include/ffi` and `/usr/include/libffi` — host Linux paths that must not be
used here.

`CFFI_FORCE_STATIC` is read from the environment, split on `;`, and passed as
`extra_objs`.

## 4. libffi provenance

Rather than picking a libffi arbitrarily, the artifact Chaquopy itself uses was
located in `target/python/build.sh`:

```bash
libs="bzip2-1.0.8-3 libffi-3.4.4-3 openssl-3.0.18-0 sqlite-3.50.4-0 xz-5.4.6-1"
url_prefix="https://github.com/beeware/cpython-android-source-deps/releases/download"
```

That is the same repository that supplied OpenSSL 3.0.18 in M0-008L-B, and it
is the only libffi artifact that has already been proven to work with the
Chaquopy Android runtime. Using it keeps the process consistent and avoids
introducing a second, differently-configured C library.

```
source   https://github.com/beeware/cpython-android-source-deps
tag      libffi-3.4.4-3
asset    libffi-3.4.4-3-aarch64-linux-android.tar.gz
bytes    42 455
LOCAL HASH (sha256, computed locally; the release publishes no checksum)
         9f2c0255ce025c177d44db16174ad5158c7560efe3c7ef0c8c0c64b2196e6a9d
```

VERIFIED FROM SOURCE + VERIFIED BY BUILD.

No patches were applied to libffi; the official artifact was used as published.

## 5. libffi Android build

**No build was needed.** The artifact is already a cross-compiled Android
AArch64 install tree:

```
include/ffi.h
include/ffitarget.h
lib/libffi.a
lib/libffi.la
lib/pkgconfig/libffi.pc
share/man/man3/ffi*.3
share/info/libffi.info
```

VERIFIED BY BUILD.

Architecture verified by extracting a member and inspecting it:

```
prep_cif.o: ELF 64-bit LSB relocatable, ARM aarch64, version 1 (SYSV)
```

`libffi.pc` declares `Version: 3.4.4`. VERIFIED FROM SOURCE.

Two build-time corrections were needed, neither of which touches cffi or
libffi source:

1. The published `.pc` hardcodes the GitHub Actions runner prefix
   (`/home/runner/work/cpython-android-source-deps/...`). Using
   `PKG_CONFIG_SYSROOT_DIR` produced a wrong include path
   (`/build/work/home/runner/...`). The `.pc` `prefix` was rewritten to
   `/build/work/libffi`, after which pkg-config resolves to
   `-I/build/work/libffi/include -lffi` with `modversion 3.4.4`.

2. The artifact ships **only** `libffi.a`, no shared library. See section 7.

## 6. cffi configuration

Mechanisms used, all read from `setup.py` rather than invented:

```
CC / LDSHARED            = NDK aarch64-linux-android24-clang
CFLAGS                   = -fPIC -I<android pydev include> -D__ANDROID_API__=24 -I<libffi include>
LDFLAGS                  = -L<ndk sysroot>/usr/lib/aarch64-linux-android/24
PKG_CONFIG_PATH          = <libffi>/lib/pkgconfig
CFFI_FORCE_STATIC        = <libffi>/lib/libffi.a
```

Compiler include paths observed during the build contain the **Android CPython
3.14 development tree** and the **Android libffi** header directory. They do not
contain `/usr/include/python3.12`, `/usr/include/ffi`, `/usr/include/libffi`, or
any host Python include. VERIFIED BY BUILD.

## 7. cffi build result

The artifact ships only `libffi.a`. Linking it dynamically would require a
`DT_NEEDED` on a `libffi.so` that Chaquopy does not ship, which would fail at
`dlopen` exactly the way the M0-008I stub did. `CFFI_FORCE_STATIC` therefore
links libffi into `_cffi_backend.so` directly.

First build succeeded but produced a defective binary:

```
_file      _cffi_backend.cpython-314-x86_64-linux-gnu.so
NEEDED     libdl.so, libc.so          <- no libpython3.14.so
undefined Py* symbols: 322, ALL type NOTYPE
```

That is the precise M0-008I failure mode: linking without the real `libpython`
leaves every CPython reference untyped, and the Android linker refuses to bind
them.

Rebuilding through a linker wrapper that forces the real runtime library
resolved it:

```bash
#!/bin/bash
exec .../aarch64-linux-android24-clang \
  -Wl,--no-as-needed \
  -L<pydev lib> -lpython3.14 \
  -Wl,--as-needed \
  "$@"
```

Final wheel: `cffi-2.1.1-cp314-cp314-android_24_arm64_v8a.whl`
(built from the official PyPI wheel `cffi-2.1.1-cp310-cp310-manylinux2014_x86_64...`,
sha256 `194cffa889098ced9976c3fc6340305e43f6303657d298da55366907c05c22d6`, with
only the native module replaced).

## 8. cffi ELF result

```
ELF 64-bit LSB shared object, ARM aarch64
e_machine 183 (AArch64)

DT_NEEDED:
  libpython3.14.so
  libdl.so
  libc.so

undefined Py* symbols: 322 total, by type
  270 FUNC
   52 OBJECT
   0 NOTYPE          <- the M0-008I failure mode is absent

undefined ffi_* symbols: 0              <- libffi is statically linked
sha256 718fd267a22cbd7f4a84b69eef3a3e7c02236c355aceebf787809e05a427c347
```

VERIFIED BY ELF.

No host library, no glibc dependency, no unexpected bundled runtime.

### Extension module naming

CPython locates extension modules by filename suffix. The official wheel names
the module `_cffi_backend.cpython-310-x86_64-linux-gnu.so`, which matches no
suffix CPython looks for on Android, so the first packaged APK could never have
imported it.

Chaquopy's own cffi wheel uses the bare name `_cffi_backend.so`. The rebuilt
wheel uses the same name.

A related packaging bug was found and fixed: the rewritten `RECORD` initially
retained an entry for the old filename, and Chaquopy resolves native modules
through `RECORD`. Gradle failed with

```
FileNotFoundError: ... pip\debug\arm64-v8a\_cffi_backend.cpython-310-x86_64-linux-gnu.so
    -> ... pip\debug\common\_cffi_backend.cpython-310-x86_64-linux-gnu.so
```

The final `RECORD` describes exactly the files present, verified
programmatically (`RECORD covers every file: True`, `stale entries: []`).

## 9. cryptography rebuild result

Rebuilt with **no changes** to OpenSSL, Python version, compiler target or
source version, exactly as required. `libffi` is not referenced by
`libcryptography_rust.so`; cryptography uses `openssl-sys` only.

## 10. cryptography ELF result

Unchanged from M0-008L-F and re-confirmed here:

```
ELF64 / AArch64
DT_NEEDED:
  libpython3.14.so
  libssl_python.so
  libcrypto_python.so
  libdl.so
  libc.so
UND PyLong_Type                      OBJECT
UND EVP_EncryptInit_ex@OPENSSL_3.0.0 FUNC
sha256 137b70f4daae4c34d247361b5157ab4ec85b92c0d69e0ac9832d58acbd14b8c3
```

No hidden host `cffi` or `libffi` dependency. VERIFIED BY ELF.

## 11. Artifact identity

The project's mandatory chain, verified end to end:

```
cffi native build  = APK embedded  = device extracted
718fd267a22cbd7f4a84b69eef3a3e7c02236c355aceebf787809e05a427c347

cryptography native build = APK embedded = device extracted
137b70f4daae4c34d247361b5157ab4ec85b92c0d69e0ac9832d58acbd14b8c3
```

Device extraction path:

```
/data/data/dev.vitngan.probe.chaquopy/files/chaquopy/AssetFinder/requirements/_cffi_backend.so
/data/data/dev.vitngan.probe.chaquopy/files/chaquopy/AssetFinder/requirements/cryptography/hazmat/bindings/_rust.abi3.so
```

The Chaquopy runtime library used for linking is still the real one from the
APK, `libpython3.14.so`
sha256 `4b99ed3aad65121db6c4c1830fba0592c0a9884a7f9a778165b6a34324059d83`,
which is the same file present in the shipped APK.

`UP-TO-DATE` was not accepted as evidence: the whole `app/build` tree was
deleted before the packaging runs.

## 12. Physical-device result

Device: Xiaomi Redmi 24069RA21C, Android 16, API 36, arm64-v8a,
`getconf PAGE_SIZE` = **4096** (so 16 KB page-size compatibility is not at issue
on this device).

```
APK_INSTALLED           OK
PYTHON_RUNTIME_OK       3.14.0
CFFI_IMPORT_OK          2.1.1
CFFI_NATIVE_OK          sizeof=16 getpid=7880
CRYPTOGRAPHY_IMPORT_OK  50.0.1
OPENSSL_RUNTIME_OK      OpenSSL 3.0.18 30 Sep 2025
SIGN_VERIFY_OK          tampered_rejected
AES_GCM_OK              invalid_rejected
X509_OK                  CN=M0-008L-F Probe CA
NATIVE_OPERATION_OK     all_operations_passed
```

VERIFIED ON DEVICE.

The reported values:

```
cffi_sizeof_struct       16          libffi struct layout, computed in _cffi_backend
cffi_getpid             7880        a real FFI call through a libffi closure
openssl_version_text    OpenSSL 3.0.18 30 Sep 2025   Chaquopy's own OpenSSL
openssl_bound           _rust.openssl
pubkey_len              32
aes_ct_len              65
aes_deterministic       True
aes_invalid_error_type  InvalidTag
aes_invalid_rejected    True
x509_subject            CN=M0-008L-F Probe CA
```

Two points matter for interpreting this as native execution rather than import:

- `InvalidTag` and `openssl_version_text()` are produced by OpenSSL inside the
  Rust extension; no Python-level fallback could produce them.
- `cffi_getpid` is a value returned by a function invoked through a libffi
  closure. That proves libffi's call machinery works on Android, not merely
  that `_cffi_backend` loaded.

Determinism is asserted rather than assumed: the same AES-256-GCM key and nonce
must produce byte-identical ciphertext twice, and an Ed25519 signature over a
tampered message must be rejected.

## 13. Negative controls

`crypto_probe.run()` raises on every failure; no exception is converted into a
PASS. The Kotlin wrapper additionally throws when
`NATIVE_OPERATION_OK` is absent from the returned result, so a probe that
silently stopped early still fails the instrumentation test.

This was not theoretical. Three probe-side defects were caught because the
tests kept failing loudly:

1. `'Parser' object has no attribute '_engine'` — an invented internal cffi
   attribute.
2. `SyntaxError: unterminated string literal` in `crypto_probe.py` — a broken
   edit, plus a UTF-8 BOM injected by a PowerShell write.
3. `module 'pydantic_core' has no attribute 'generate_schema'` — a pre-existing
   defect in the older `probe_native.py` probe, which referenced a
   `schema_generator` submodule that does not exist in pydantic-core 2.46.4.
   It is now aligned with the API that M0-008K-FIX actually proved working
   (`core_schema.int_schema()` + `SchemaValidator` + `SchemaSerializer`).

Every one of these was fixed in the probe, never by weakening the assertion.

## 14. Remaining blocker

**None for this chain.** libffi, cffi and cryptography are all built, packaged
and executing.

## 15. What is now proven about the broader dependency closure

PROVEN on the physical device, with unmodified upstream sources:

- `pydantic-core 2.46.4` (Rust/PyO3)
- `cffi 2.1.1` (C + libffi 3.4.4)
- `cryptography 50.0.1` (Rust + OpenSSL 3.0.18)

Still untouched: `orjson`, `jiter`, `Pillow`, `PyNaCl`, `httptools`,
`watchfiles`, `pillow-heif`, `resvg-py`. `pillow-heif` and `resvg-py` remain
the expected hard cases because they carry third-party native libraries, and
this milestone showed exactly what that costs: libffi had to be sourced,
verified and statically linked by hand.

## 16. Supply-chain notes

| Item | Provenance |
|---|---|
| libffi 3.4.4 | beeware/cpython-android-source-deps, tag `libffi-3.4.4-3`, Android aarch64 |
| cffi 2.1.1 | official PyPI sdist, unmodified |
| cryptography 50.0.1 | official PyPI sdist, unmodified |
| OpenSSL 3.0.18 | unchanged, shared with the Chaquopy runtime |
| `vendored` | never enabled |
| Termux / root / Shizuku | not used |
| binaries committed | none |

No legal conclusion is drawn. The shared-OpenSSL route was used, no second
OpenSSL is bundled, and libffi is bundled statically into `_cffi_backend.so`
inside the app's own sandbox, which is an ordinary application dependency.

## 17. Next step

The remaining Hermes native dependencies, one at a time. `httptools` (pure C,
no third-party native libraries) is the cheapest next test and will show
whether the toolchain generalises beyond packages that already had a
beeware-sourced dependency available. `pillow-heif` and `resvg-py` should be
attempted last.
