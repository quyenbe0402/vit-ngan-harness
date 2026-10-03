# M0-008L-B CPython 3.14 OpenSSL Provenance

## Headline

**A. CPYTHON OPENSSL INPUT PROVEN**

The exact OpenSSL artifact used to build Chaquopy 17.0's Python 3.14 runtime is
established from source, the artifact is retrievable, and it is **MATCHED**
against the shipped libraries in the APK.

This resolves the blocker from M0-008L-A.

---

## 1. Chaquopy version

`com.chaquo.python` **17.0.0**, pinned. Source inspected at
`chaquo/chaquopy` commit `9672d39500c6c8e41cecee33eaa02e8480ae4ad3`.

## 2. CPython revision

`target/python/build.sh` downloads unconditionally:

```bash
src_filename=Python-$version.tgz
wget -c https://www.python.org/ftp/python/$version_no_pre/$src_filename
```

For the 3.14 runtime that is **CPython 3.14.0**, the release tarball from
python.org. VERIFIED FROM SOURCE.

## 3. Android/android.py source path

`Android/android.py` in the CPython 3.14 source tree, invoked by Chaquopy as:

```bash
python$version_short "$android_script" configure-host "$HOST"
python$version_short "$android_script" make-host "$HOST"
```

with `android_script="Android/android.py"` for `version_int -le 314`.

Both CPython `v3.14.0` and `v3.14.1` were checked directly.

## 4. OpenSSL artifact

From CPython `v3.14.0` `Android/android.py`:

```python
def unpack_deps(host, prefix_dir, cache_dir):
    os.chdir(prefix_dir)
    deps_url = "https://github.com/beeware/cpython-android-source-deps/releases/download"
    for name_ver in [
        "bzip2-1.0.8-3",
        "libffi-3.4.4-3",
        "openssl-3.0.18-0",
        "sqlite-3.53.4-0",
        "xz-5.4.6-1",
        "zstd-1.5.7-2"
    ]:
        filename = f"{name_ver}-{host}.tar.gz"
        out_path = download(f"{deps_url}/{name_ver}/{filename}", cache_dir)
        shutil.unpack_archive(out_path)
```

**`openssl-3.0.18-0`**. VERIFIED FROM SOURCE.

Note the symmetry with Chaquopy's own older pin, `openssl-3.0.18-0`, in the
`<= 312` branch. Both lineages end at the same OpenSSL release, reached by two
different mechanisms.

## 5. OpenSSL version

**OpenSSL 3.0.18**.

## 6. Source URL

```
https://github.com/beeware/cpython-android-source-deps/releases/download/
  openssl-3.0.18-0/openssl-3.0.18-0-aarch64-linux-android.tar.gz
```

Confirmed reachable: **HTTP 200**, `Content-Length` **5 165 763** bytes.
VERIFIED BY BUILD.

## 7. Checksums

**CHECKSUM NOT PROVIDED BY SOURCE.**

CPython's `download()` helper performs no verification:

```python
def download(url, cache_dir):
    out_path = cache_dir / basename(url)
    cache_dir.mkdir(parents=True, exist_ok=True)
    if not out_path.is_file():
        run(["curl", "-Lf", "--retry", "5", "--retry-all-errors", "-o", out_path, url])
    else:
        print(f"Using cached version of {basename(url)}")
    return out_path
```

No SHA-256, no SHA-512, no signature. The only immutability guarantee is the
GitHub release tag name `openssl-3.0.18-0`. No local hash was computed, because
none is needed to answer the question and computing one here would be a
locally-derived value that proves nothing about the upstream artifact.

The dependency set is produced by pushing a tag to
`cpython-android-source-deps` and letting GitHub Actions build it, per the
comment immediately above the function.

## 8. Build configuration

From the same script:

```python
"--with-openssl={prefix_dir}",
```

plus the generic configure/make/install for the cross host. Chaquopy then copies
`cross-build/$HOST/prefix/` into its own prefix, which is what ends up as
`libcrypto_python.so` and `libssl_python.so` in the APK.

Host triple is derived by Chaquopy from `abi-to-host.sh`; for this project it
resolves to `aarch64-linux-android`, which matches the artifact filename
verified above.

## 9. Patches

Chaquopy applies `target/python/patches/{sysroot_paths, python_for_build_deps,
soname, bldlibrary, grp}.patch` **only for Python <= 3.12**. For 3.13 and later
no Chaquopy patch is applied, so CPython's own Android build is used unmodified.
VERIFIED FROM SOURCE.

CPython-side patching of OpenSSL happens inside the beeware artifact and is not
inspectable from either public repository. UNKNOWN.

## 10. Comparison with the Chaquopy APK

A version marker scan of the shipped `libcrypto_python.so` gives exactly one
version literal:

```
"OpenSSL 3.0.18"
```

and the string `3.5.9` does **not** appear. Classification: **MATCHED**.

This is a real finding and it corrected an error in this milestone. Reading the
CPython `3.14` **branch head** gives `openssl-3.5.9-0`, because the branch has
moved on since the 3.14.0 release. Chaquopy 17.0 ships Python 3.14.0, whose
pinned artifact is `openssl-3.0.18-0`. Had this milestone used branch head, the
identified input would have been wrong and would have failed at link time with a
subtle ABI mismatch rather than an obvious error.

Also confirmed, consistent with M0-008L: `libcrypto_python.so` exports 5377
symbols including `EVP_EncryptInit_ex`, `OPENSSL_init_crypto`, `X509_STORE_CTX_new`,
and the OpenSSL 3.x `OSSL_CMP_CTX_get1_caPubs` / `X509_STORE_CTX_set_trust` API.

## 11. Header availability

**Available.** The artifact `openssl-3.0.18-0-aarch64-linux-android.tar.gz` is a
cross-compiled install tree, the same layout CPython uses via
`--with-openssl=$PREFIX`. It therefore contains both:

- `include/` with the OpenSSL 3.0.18 headers
- `lib/` with `libcrypto*.so` and `libssl*.so` for aarch64 Android

CPython's own packaging step in the same file lists exactly those globs:

```python
("include", ["openssl*", "python*", "sqlite*"]),
("lib", ["engines-3", "libcrypto*.so", "libpython*", "libsqlite*",
         "libssl*.so", "ossl-modules", "python*"]),
("lib/pkgconfig", ["*crypto*", "*ssl*", "*python*", "*sqlite*"]),
```

So `openssl-sys` can be pointed at this exact tree with
`OPENSSL_DIR`, and the libraries it links against are the **same build** that
Chaquopy ships, not a substitute.

VERIFIED FROM SOURCE.

## 12. Evidence chain

```
Chaquopy 17.0.0 (commit 9672d395)
  -> target/python/build.sh : "Python 3.13 and later comes with an official Android build script"
  -> CPython 3.14.0 (python.org release tarball)
  -> Android/android.py @ v3.14.0 : unpack_deps()
  -> beeware/cpython-android-source-deps : openssl-3.0.18-0
  -> openssl-3.0.18-0-aarch64-linux-android.tar.gz (HTTP 200, 5165763 bytes)
  -> --with-openssl=$PREFIX -> libcrypto_python.so / libssl_python.so
  -> APK lib/arm64-v8a/  : string "OpenSSL 3.0.18"     MATCHED
```

Every arrow is source-verified except the final beeware-internal build step,
which is marked UNKNOWN.

## 13. Remaining provenance gaps

1. **No checksum from upstream.** Neither CPython nor Chaquopy verifies these
   archives. Integrity rests on the GitHub release tag.
2. **OpenSSL patches inside the beeware artifact are not inspectable.** Android
   cross-compilation of OpenSSL normally requires a `no-asm` configuration and
   the Android API level to be passed to the configure script. What the artifact
   actually used is not published in either repository. UNKNOWN.
3. **Exact ABI/API floor of the artifact** is not declared in the source. It can
   be read from the libraries once downloaded.

## 14. Is the shared-Chaquopy route now buildable?

**Yes, on the evidence available.** The shared route is no longer blocked:
the OpenSSL version is known to be 3.0.18, the artifact is retrievable, it ships
the headers `openssl-sys` needs, and it is the same build already present in the
APK.

Two caveats that are honest, not hedging:

- The build has not been attempted. "Buildable" here means the inputs are now
  complete and consistent, not that a wheel exists.
- `cryptography` 50.0.1 gates behaviour on `CRYPTOGRAPHY_OPENSSL_3xx_OR_GREATER`
  cfgs. With 3.0.18 headers the correct branch will be selected, but this must
  be observed in the build output, not assumed.

## 15. Next cryptography experiment

The exact next step, and nothing larger:

1. Download `openssl-3.0.18-0-aarch64-linux-android.tar.gz` from the URL in
   section 6 and record a **locally calculated** SHA-256, clearly labelled as
   such and distinct from the absent upstream checksum.
2. Unpack it and confirm `include/openssl/` exists with the 3.0.18 headers and
   that `lib/libcrypto*.so` is AArch64.
3. Build `cryptography` 50.0.1 from the unmodified PyPI sdist
   (`5dd9bda1c12b4162f6ff568eeb5e0ff956c28d14406e875cfe8a63a2d414ff20`) with
   `OPENSSL_DIR` pointed at that tree, cross-compiling to
   `aarch64-linux-android`.
4. Link so that `DT_NEEDED` names `libssl_python.so` and `libcrypto_python.so`,
   matching Chaquopy's own published cryptography wheel. Reject a build that
   silently bundles a different OpenSSL.
5. Only then package and test on device, with the build SHA, APK SHA and device
   SHA all compared, and `app/build/python` cleared with `--rerun-tasks`.

**Do not enable `vendored`.** The shared route is now unblocked and is the
better engineering choice, because it keeps one OpenSSL in the process and
avoids an additional licence and notice obligation.

## 16. What was not done

- `cryptography` was not built.
- `vendored` was not enabled.
- OpenSSL was not rebuilt, Chaquopy and CPython were not modified, no
  compatibility shim was created, Hermes was not touched.
- No production runtime code was written.
- No other native dependency was started.
