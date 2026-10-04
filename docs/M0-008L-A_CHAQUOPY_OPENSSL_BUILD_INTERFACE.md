> **HISTORICAL RECORD.**
> This document records the state and evidence at the time of its milestone.
> Its historical contents are preserved unchanged, including any conclusion
> later overtaken by newer evidence or by the architecture pivot. Do not read
> it as current architecture guidance.
> Current architecture decisions are governed by `docs/PIVOT-DECISION.md`.

# M0-008L-A Chaquopy OpenSSL Build Interface

## Headline

**B. SHARED ROUTE BLOCKED - VENDORED ROUTE REMAINS**

The exact OpenSSL build interface Chaquopy 17.0 uses has been identified from
Chaquopy's own build scripts, not inferred from binary strings. The shared
route is not reproducible from Chaquopy's public repository, for a specific and
documented reason. The vendored route remains technically available but was not
exercised, per instruction.

---

## 1. Hermes cryptography requirement

Unchanged and re-verified from `pyproject.toml` at `eaecc99c`:

```
cryptography==50.0.1; python_version >= '3.14'
```

Android-active, pinned for CVE-2026-69247, GHSA-m2h6-j472-rp4c,
GHSA-jwv3-5hgf-82ww, CVE-2026-39892, CVE-2026-34073, GHSA-537c-gmf6-5ccf, also a
transitive requirement of `PyJWT[crypto]==2.13.0`.

## 2. Chaquopy OpenSSL architecture

Chaquopy does not build OpenSSL itself for modern Python. It builds CPython,
and CPython's own Android build system supplies OpenSSL.

From `target/python/build.sh` in `chaquo/chaquopy` at commit
`9672d39500c6c8e41cecee33eaa02e8480ae4ad3`:

```bash
# Remove any existing installation in the prefix.
...
if [ $version_int -le 312 ]; then
    # Download and unpack libraries needed to compile Python. For a given Python
    # version, we must maintain binary compatibility with existing wheels.
    libs="bzip2-1.0.8-3 libffi-3.4.4-3 openssl-3.0.18-0 sqlite-3.50.4-0 xz-5.4.6-1"
    url_prefix="https://github.com/beeware/cpython-android-source-deps/releases/download"
    ...
fi
```

and further down, for newer Python:

```bash
  make install prefix=$PREFIX

  # Python 3.13 and later comes with an official Android build script.
  else
      mkdir -p cross-build/build
      ln -s "$(which python$version_short)" cross-build/build/python

      if [ $version_int -le 314 ]; then
          android_script="Android/android.py"
      else
          android_script="Platforms/Android"
      fi

      python$version_short "$android_script" configure-host "$HOST"
      python$version_short "$android_script" make-host "$HOST"
      cp -a "cross-build/$HOST/prefix/"* "$PREFIX"
  fi
```

VERIFIED FROM SOURCE.

**This is the answer to the primary question.** For Python 3.14, Chaquopy does
not build OpenSSL at all. It delegates to **CPython 3.14's own official
`Android/android.py`** (PEP 738 cross-build path). The `libcrypto_python.so`
and `libssl_python.so` shipped in the runtime are produced by that script, not
by Chaquopy.

## 3. Exact source and version evidence

Two different answers depending on Python version, and this distinction is the
whole story:

| Chaquopy target | OpenSSL source | Where it is pinned |
|---|---|---|
| Python <= 3.12 | `openssl-3.0.18-0` from `beeware/cpython-android-source-deps` | `target/python/build.sh` line 62, in Chaquopy's own repo |
| Python 3.13 / 3.14 | whatever CPython 3.14's `Android/android.py` pins | **CPython's repository, not Chaquopy's** |

So the `openssl-3.0.18-0` pin is real and reproducible, but it belongs to the
Python 3.12 and older branch. **It does not describe the Python 3.14 runtime
this project uses.** Reading it as the 3.14 version would be a mistake, and it
is the exact mistake the milestone warned against.

VERIFIED FROM SOURCE.

## 4. Header provenance

For Python 3.14, the headers come from the CPython 3.14 `Android/android.py`
dependency set, which uses the same `beeware/cpython-android-source-deps`
release family as the older Chaquopy path, but at whatever version CPython
3.14 pins.

Two consequences:

1. **Chaquopy's repository does not contain the OpenSSL headers or the exact
   3.14 OpenSSL version.** That input lives in the CPython and beeware
   repositories.
2. Reproducing the shared route therefore means matching **CPython 3.14's**
   pin, not Chaquopy's. That is a reproducible public input, but it is outside
   Chaquopy's repository and was not resolved within this milestone.

Neither `libcrypto_python.so` nor `libssl_python.so` ships headers in the APK,
confirmed by enumerating every archive entry. VERIFIED FROM APK.

## 5. Build configuration, as far as it is published

From `target/python/build.sh`:

```bash
configure_args="--host=$HOST --build=$(./config.guess) --enable-shared --without-ensurepip --with-openssl=$PREFIX"
configure_args+=" --enable-ipv6"
```

plus a `config.site` that overrides tests which cannot run while
cross-compiling:

```
ac_cv_aligned_required=no
ac_cv_file__dev_ptm=no
ac_cv_file__dev_ptc=no
```

The `soname` patch in `target/python/patches/soname.patch` is applied only for
Python <= 3.12. That is significant: for 3.13 and later, Chaquopy does not
rewrite SONAMEs itself, and the `_python` suffixed library names come from
CPython's own build layout rather than a Chaquopy patch.

VERIFIED FROM SOURCE.

NOT ESTABLISHED: the compiler flags, exact NDK, API level and link flags used
by CPython 3.14's `Android/android.py`. These are in CPython's repository, not
Chaquopy's, and were not resolved here.

## 6. ELF comparison

Already established in M0-008L and unchanged:

| Library | Size | Exported |
|---|---|---|
| `libcrypto_python.so` | 3634 KB | 5377 symbols |
| `libssl_python.so` | 609 KB | 517 symbols |

Both AArch64, with OpenSSL 3.x APIs present (`OSSL_CMP_CTX_get1_caPubs`,
`X509_STORE_CTX_set_trust`, `EVP_EncryptInit_ex`, `OPENSSL_init_crypto`).

Chaquopy's published cryptography wheel, `cryptography-42.0.8-1-cp313-cp313-android_24_arm64_v8a.whl`,
has `_rust.so` with:

```
DT_NEEDED  libpython3.13.so  libssl_python.so  libcrypto_python.so
           libdl.so  libc.so
```

So the shared route is architecturally confirmed to be the intended one. Only
the build inputs are missing.

**Source-to-binary identity is NOT claimed.** I can show that Chaquopy
delegates to CPython 3.14's Android build, and that the shipped libraries are a
genuine OpenSSL 3.x, but I did not trace the exact OpenSSL revision that
`Android/android.py` selected, so an exact source-identity claim would be
unearned.

## 7. Shared-route build result

**Not performed.** The prerequisite - a known OpenSSL version with matching
headers - was not established, and building against a guessed version would
risk compiling the wrong `CRYPTOGRAPHY_OPENSSL_3xx_OR_GREATER` code paths
silently. That is precisely the failure mode the milestone asked to avoid, so
no speculative build was run.

## 8. Physical-device result

Not reached. No wheel was produced.

## 9. First concrete blocker

**The OpenSSL version used to build Chaquopy's Python 3.14 runtime is not
defined in Chaquopy's repository.** For Python 3.13 and later, Chaquopy
delegates the whole cross-build to CPython's `Android/android.py`, so the
OpenSSL pin lives in CPython's repository, not Chaquopy's. Chaquopy publishes no
headers, no OpenSSL version for the 3.14 branch, and no recipe for it.

This is a **build-input provenance gap**, not a capability limit and not an
Android limitation. The same libraries have already been shown to load and
export a complete OpenSSL API on the device.

## 10. Vendored route status

**Remains technically viable.** `openssl-src` cross-compiles OpenSSL for
`aarch64-linux-android` from source using the NDK, with no header matching and
no dependence on Chaquopy's build. Enabling it is a one-line change to the
workspace `Cargo.toml`:

```toml
openssl-sys = { version = "0.9.116", features = ["vendored"] }
```

Not applied in this milestone, per instruction.

A third option also exists and was not evaluated: resolve CPython 3.14's actual
`Android/android.py` pin and build against those exact headers. That would keep
one OpenSSL in the process. It was not attempted because it is the same class of
work as the vendored route and the milestone scope was the shared route only.

## 11. Supply-chain implications

| Artefact | Provenance | Hash |
|---|---|---|
| Chaquopy source | `github.com/chaquo/chaquopy` | commit `9672d39500c6c8e41cecee33eaa02e8480ae4ad3` |
| OpenSSL for Python <= 3.12 | `beeware/cpython-android-source-deps`, `openssl-3.0.18-0` | not fetched |
| OpenSSL for Python 3.14 | pinned by CPython 3.14 `Android/android.py` | **UNKNOWN** |

Chaquopy's build script uses `curl -Lf --retry 5` and `wget -c` with **no
checksum verification** on those dependency archives. That is worth recording
as a supply-chain observation about the upstream build, not as an accusation:
it simply means the pinned version string is not backed by a hash inside
Chaquopy's own repository.

No OpenSSL sources, wheels, `.so` files, APKs, keys or credentials are
committed by this milestone.

## 12. Product and legal review boundary

No legal conclusion is drawn here.

Recorded as facts only:

- The shared route implies reusing an OpenSSL that Chaquopy already ships.
- The vendored route implies a second OpenSSL inside the application's process
  and therefore additional third-party notice obligations.
- Neither choice is made here.

If vendored OpenSSL is chosen: **PRODUCT/LEGAL REVIEW REQUIRED**. This is a
boundary marker, not a technical pass or fail.

## 13. Stop-condition answers

**1. What exact OpenSSL source/version does Chaquopy 17.0 use?**
For Python <= 3.12 it is `openssl-3.0.18-0` from
`beeware/cpython-android-source-deps`, pinned in
`target/python/build.sh`. For Python 3.13 and later, which includes the 3.14
this project uses, Chaquopy does not pin OpenSSL at all: it delegates to
CPython's official `Android/android.py`, so the version is CPython's choice and
was not resolved here. VERIFIED FROM SOURCE for the delegation;
UNKNOWN for the resulting 3.14 version.

**2. Can its headers and build inputs be reproduced?**
From Chaquopy's repository alone, **no**. There is no 3.14 OpenSSL pin, no
headers, and no recipe in `chaquo/chaquopy`. Reproducing it requires following
the delegation into CPython 3.14's own Android build inputs.

**3. Can cryptography 50.0.1 build against the same OpenSSL interface?**
Unknown, because the interface was not pinned. Not attempted, deliberately.

**4. First concrete blocker?**
The OpenSSL version for the Python 3.14 runtime is defined in CPython's
repository rather than Chaquopy's, so Chaquopy's public source does not contain
the build input needed to compile against its own shipped libraries.

**5. Does the vendored route remain technically viable?**
Yes. It is a one-line Cargo change and removes the header-matching problem
entirely. Not exercised in this milestone.

**6. What exact experiment should happen next?**
Two candidates, in order of cost:

(a) Cheap and decisive: read CPython 3.14's `Android/android.py` and its
dependency manifest to recover the exact `openssl-<version>-<n>` artifact it
uses, download it from `beeware/cpython-android-source-deps`, and record its
SHA-256. If that succeeds, the shared route becomes buildable with a known
version.

(b) If (a) does not yield a pinned version quickly, apply the vendored patch,
build, and accept the extra OpenSSL copy plus the product and legal review.

## 14. What was not done

- No `cryptography` wheel was built.
- No device test ran.
- The vendored route was not exercised.
- No other native dependency was started.
- Hermes was not imported, initialised or modified.
- No production runtime code was written.
