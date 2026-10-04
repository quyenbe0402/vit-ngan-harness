> **HISTORICAL RECORD.**
> This document records the state and evidence at the time of its milestone.
> Its historical contents are preserved unchanged, including any conclusion
> later overtaken by newer evidence or by the architecture pivot. Do not read
> it as current architecture guidance.
> Current architecture decisions are governed by `docs/PIVOT-DECISION.md`.

# M0-008L-E Host CPython 3.14 and cryptography

## Headline

**B. CRYPTOGRAPHY BUILD STILL BLOCKED**

Host CPython 3.14.0 was provisioned and validated, which removes the M0-008L-D
blocker. The Android development tree and the cryptography build did **not**
complete in this milestone, for a reason that is my own build-harness mistake
rather than a technical obstacle.

---

## 1. Host Python source and provenance

| Item | Value |
|---|---|
| Project | `astral-sh/python-build-standalone` |
| Release tag | `20251014` |
| Artifact | `cpython-3.14.0+20251014-x86_64-unknown-linux-gnu-install_only.tar.gz` |
| URL | `https://github.com/astral-sh/python-build-standalone/releases/download/20251014/cpython-3.14.0%2B20251014-x86_64-unknown-linux-gnu-install_only.tar.gz` |
| Size | 119.38 MB |
| Format | gzip (`1F 8B 08 00` magic verified) |
| **LOCAL HASH** (sha256, locally calculated) | `74d4516a64abc63ae4bcbffb35482879a85b7faa187fcfa47c1ca8f00faebf5f` |

The version was chosen deliberately. Recent python-build-standalone releases
only ship CPython 3.14.8, so an older tag was required to obtain **exactly
3.14.0** as the milestone specifies. Version 3.14.0 was not approximated with
3.14.8.

Installed to an isolated path, not replacing `/usr/bin/python3`.

## 2. Exact host version - validated

```
Python 3.14.0
sys.version = 3.14.0
machine = x86_64
stdlib imports OK
pip = pip 25.2 from /opt/python314/lib/python3.14/site-packages/pip (python 3.14)
```

VERIFIED BY BUILD. This is the blocker from M0-008L-D removed.

Note the distinction the milestone asked to preserve: this is the **host build
interpreter**, `x86_64`. The **target** remains Android `aarch64-v8a`
CPython 3.14.0.

## 3. A build-harness mistake that cost this milestone

The first configure attempt failed with:

```
configure: error: invalid or missing build python binary "/opt/python314/bin/python3"
```

That was **my error, not CPython's**. The host interpreter had been extracted
into the container filesystem at `/opt/python314`, and the container was run
with `--rm`, so the interpreter no longer existed when the next container
started. I then repeated the mistake by storing it in `/opt` again inside the
container rather than in a persistent Docker volume, and by failing to copy the
119 MB archive into that volume before the run that depended on it.

The fix is known and small: extract the host interpreter into the persistent
volume (`/w/hostpy`) and point `--with-build-python` at that path. It was not
completed before the milestone budget ran out.

This is recorded plainly because it is the actual reason the cryptography build
did not proceed, and it is not attributable to CPython, cryptography, OpenSSL,
Chaquopy or Android.

## 4. CPython Android configure

Source: `https://www.python.org/ftp/python/3.14.0/Python-3.14.0.tgz`.
LOCAL HASH computed in the build container: `88d2da4eed42fa9a5f42ff58a8bc8988881bd6c547e297e46682c2687638a851`.

Configure invocation prepared, using only options CPython 3.14 accepts. The
M0-008L-D `unrecognized options: --with-cc, --with-ld` warning is gone; those
were replaced with the `CC` and `AR` environment variables:

```bash
HOST=aarch64-linux-android ; API=24
CC=$TC/${HOST}${API}-clang
AR=$TC/llvm-ar
CFLAGS="-O3 -fPIC -I$TC/../sysroot/usr/include"
LDFLAGS="-L$TC/../sysroot/usr/lib/$HOST/$API"
./configure --host=$HOST --build=$(./config.guess)   --with-build-python=<host 3.14>   --enable-shared --without-ensurepip --with-openssl=/openssl
```

**Result: NOT COMPLETED.** configure did not run to completion, so no generated
`pyconfig.h` exists.

## 5. Development tree and pyconfig.h evidence

**Not produced.** Consequently:

- no `Python.h` / `pyconfig.h` / `pyconfig_aarch64-linux-android.h` to inspect
- no verification that `pyconfig.h` is Android-targeted
- no verification that it is free of `/usr/include/python3.12`

Nothing is claimed about the contents of a file that was never generated. This
is the same discipline applied in M0-008L-D.

The only confirmed datapoint remains from M0-008L-D: configure correctly
reports `checking host system type... aarch64-unknown-linux-android`, so the
Android build path is engaged.

## 6. PyO3 / CFFI configuration

Unchanged from M0-008L-C and still correct:

- `PYO3_CROSS_PYTHON_VERSION=3.14`
- `PYO3_CROSS_LIB_DIR` pointing at the Android tree (still to be produced)
- `PYO3_CROSS_PYTHON_IMPLEMENTATION=CPython`

Not exercised, because the tree does not exist yet.

## 7. Compiler include paths and host contamination

**Not verified.** The compile step never ran. The M0-008L-C contamination
(`-I /usr/include/python3.12`) therefore has not been shown to be removed.

No claim is made that host contamination is gone.

## 8. cryptography build result

**Not attempted.** The prerequisite dev tree does not exist.

## 9. ELF result

**None.** No `.so` was produced.

## 10. OpenSSL

Unchanged and still valid: the `openssl-3.0.18-0` tree, LOCAL HASH
`2ab2d0caf1d32a77a7ddb84ef92ff5ef518b7b1754178e96649555b510edbade`, with
`libcrypto_python.so` / `libssl_python.so` already carrying the correct
SONAMEs. `vendored` remained off for the whole milestone. No OpenSSL was
switched.

## 11. Device status

Not reached. No wheel, no APK, no installation.

## 12. First remaining blocker

**My build harness discards the host interpreter between container runs.**

It is a two-line fix: extract python-build-standalone into the persistent Docker
volume and pass `--with-build-python=/w/hostpy/bin/python3`. Once that lands,
configure should complete, the Android dev tree should generate, and the
M0-008L-C cryptography build can resume with `PYO3_CROSS_LIB_DIR` set.

This is not a technical blocker and should not be presented as one.

## 13. Stop-condition answers

**1. Was host CPython 3.14.0 provisioned?**
**Yes.** python-build-standalone `20251014`, LOCAL HASH
`74d4516a64abc63ae4bcbffb35482879a85b7faa187fcfa47c1ca8f00faebf5f`, reporting
`Python 3.14.0` with working stdlib and pip.

**2. Did CPython Android configure succeed?**
**No.** It did not run to completion, because the host interpreter it was
pointed at did not exist in the new container.

**3. Was a correct Android CPython 3.14 development tree generated?**
**No.** Nothing was generated, and no properties are claimed for it.

**4. Did PyO3/CFFI stop consuming host Python 3.12?**
**Not verified.** The compile step never ran.

**5. Did cryptography 50.0.1 compile/link?**
**No.** Not attempted.

**6. First remaining blocker?**
The build harness losing the host interpreter between `--rm` container runs.
A harness defect on my side, with a known two-line remedy.

## 14. What remains proven from earlier milestones

Unchanged:

- OpenSSL 3.0.18 artifact recovered and verified, correct SONAMEs.
- `openssl-sys` demonstrably consumes that tree.
- Chaquopy's `libpython3.14.so` links and loads; `pydantic-core` native
  operation proven with Rust-generated `int_parsing`.
- CPython's Android configure correctly detects `aarch64-unknown-linux-android`.

## 15. Supply-chain notes

| Artefact | Hash | Kind |
|---|---|---|
| host CPython 3.14.0 build | `74d4516a64abc63ae4bcbffb35482879a85b7faa187fcfa47c1ca8f00faebf5f` | LOCAL HASH |
| CPython 3.14.0 source tarball | `88d2da4eed42fa9a5f42ff58a8bc8988881bd6c547e297e46682c2687638a851` | LOCAL HASH |
| `openssl-3.0.18-0-aarch64-linux-android.tar.gz` | `2ab2d0caf1d32a77a7ddb84ef92ff5ef518b7b1754178e96649555b510edbade` | LOCAL HASH |
| `cryptography-50.0.1.tar.gz` | `5dd9bda1c12b4162f6ff568eeb5e0ff956c28d14406e875cfe8a63a2d414ff20` | upstream |

None of these are upstream-provided checksums. No CPython binary distribution,
wheel, `.so`, APK, OpenSSL archive, keystore, token or credential is committed.

## 16. What was not done

- The cryptography build was not resumed.
- No ELF was produced and no acceptance check was performed.
- No device test ran.
- `vendored` was not enabled.
- No cryptography source was patched; no Hermes change of any kind.
- No production runtime code was written.
