# M0-008L cryptography Android Wheel

## Headline

**INCOMPLETE - no classification claimed.**

No `cryptography` wheel was produced and no device test ran. Assigning A, B, C
or D here would be unearned. This document records what was verified, the
precise blocker, and the two ways forward.

The architecture turned out to be **favourable**, which is the substantive
finding. The blocker is narrow, specific, and a supply-chain detail rather than
a capability limit.

---

## 1. Exact Hermes requirement

Recomputed from `pyproject.toml` at `NousResearch/hermes-agent` @ `eaecc99c`,
not reused from an earlier milestone. VERIFIED FROM SOURCE.

```
cryptography==50.0.1; python_version >= '3.14'
```

It appears twice: once in `[project].dependencies`, and once again in the
`[tool.uv]` override block. The comments pin it deliberately and cite
**CVE-2026-69247, GHSA-m2h6-j472-rp4c, GHSA-jwv3-5hgf-82ww, CVE-2026-39892,
CVE-2026-34073, GHSA-537c-gmf6-5ccf**.

Two things matter for Android:

- The marker is `python_version >= '3.14'`, so it is **Android-active** and is
  exactly the gate that made this package look impossible under Chaquopy's
  cp313-only native wheels.
- It is also a **transitive** requirement of `PyJWT[crypto]==2.13.0`, so it is
  needed regardless of the direct pin.

A further constraint is recorded in the same file: an `alibabacloud-tea-openapi`
dependency caps `cryptography<49`, and the `[tool.uv]` override exists
specifically to break that cap. Any reconstruction that silently drops to 48.x
would not match Hermes.

**Pinned version for this milestone: `cryptography==50.0.1`.**

## 2. Exact cryptography version

`50.0.1`, released on PyPI, `requires_python !=3.9.0,!=3.9.1,>=3.9`.
VERIFIED FROM SOURCE.

## 3. Source provenance

| Item | Value |
|---|---|
| sdist | `cryptography-50.0.1.tar.gz` |
| size | 860 KB |
| SHA-256 | `5dd9bda1c12b4162f6ff568eeb5e0ff956c28d14406e875cfe8a63a2d414ff20` |
| build backend | `maturin` (from `build-backend = "maturin"`) |
| Rust | workspace at `src/rust`, plus `cryptography-cffi`, `cryptography-crypto`, `cryptography-openssl`, `cryptography-key-parsing`, `cryptography-x509`, `cryptography-x509-verification` |
| pyo3 | `0.29`, features `["abi3", "abi3t"]` |
| openssl-sys | `0.9.116` |
| rust-version | from workspace |

No git fork. No patch applied. The sdist is unmodified.

VERIFIED FROM SOURCE.

## 4. Build toolchain (planned, matching the proven pydantic-core recipe)

- NDK r27c
- Rust 1.99.0, target `aarch64-linux-android`
- `aarch64-linux-android24-clang`
- CPython 3.14 link target

Not executed. Listed so the next milestone can reuse it verbatim.

## 5. OpenSSL architecture - the substantive finding

This is where the milestone's real value lies.

### cryptography does not vendor OpenSSL

The workspace declares:

```toml
openssl-sys = "0.9.116"
```

with **no `vendored` feature**. So `cryptography` 50.0.1 expects an **external**
OpenSSL discovered at build time via `OPENSSL_DIR` / `OPENSSL_LIB_DIR` /
`OPENSSL_INCLUDE_DIR`. VERIFIED FROM SOURCE.

### Chaquopy already ships a real OpenSSL

The probe APK contains, under `lib/arm64-v8a/`:

| Library | Size |
|---|---|
| `libcrypto_python.so` | 3634 KB |
| `libssl_python.so` | 609 KB |
| `libcrypto_chaquopy.so` | 4 KB |
| `libssl_chaquopy.so` | 4 KB |

ELF analysis of `libcrypto_python.so`: AArch64, **5377 exported symbols**,
including `EVP_EncryptInit_ex`, `OPENSSL_init_crypto`, `X509_STORE_CTX_new`,
`OpenSSL_version`, `OpenSSL_version_num`. `libssl_python.so` exports the SSL
API (`SSL_CTX_new`, `SSL_set_bio`, `SSL_set_verify_depth`, ...).

This is a genuine **OpenSSL 3.x** build: the symbol set includes
`OSSL_CMP_CTX_get1_caPubs` and `X509_STORE_CTX_set_trust`, which are OpenSSL
3.0+ APIs. VERIFIED FROM APK.

### Chaquopy's own cryptography wheel links exactly these

Disassembling Chaquopy's published
`cryptography-42.0.8-1-cp313-cp313-android_24_arm64_v8a.whl` gives:

```
_rust.so  ELF64 AArch64
  DT_NEEDED  libpython3.13.so
             libssl_python.so
             libcrypto_python.so
             libdl.so
             libc.so
```

So Chaquopy's supported recipe for cryptography is: **link the Rust extension
against Chaquopy's own Python and Chaquopy's own OpenSSL.** That is exactly the
shape we need, and it means **no Linux OpenSSL and no Termux OpenSSL is
required or permitted**. The Android security boundary is preserved.

VERIFIED FROM APK.

### Why `pydantic-core`'s recipe transfers

The pydantic-core success came from linking the Rust `.so` against Chaquopy's
real `libpython3.14.so` instead of a stub, so that undefined CPython references
carry the correct ELF symbol type. `cryptography` needs the same, plus two more
libraries in the same family. The mechanism is identical.

## 6. The blocker

`openssl-sys` must **configure and detect** the OpenSSL it links against. That
requires OpenSSL **headers**, not just libraries.

**The Chaquopy APK ships no OpenSSL headers.** Verified by enumerating every
entry in the APK: no `openssl/` directory and no `.h` files. VERIFIED FROM APK.

Consequences:

1. The exact OpenSSL version and build configuration Chaquopy used are not
   discoverable from the APK. A version string scan of `libcrypto_python.so`
   returned only ambiguous fragments (`3.0.18`, `3.1.9`, `3.36.3`, ...) with
   no unambiguous `OpenSSL 3.x.y` literal, so the version could not be pinned.
2. Fetching headers for a guessed version risks an ABI mismatch with the
   libraries actually present in the APK. `cryptography` gates behaviour on
   `CRYPTOGRAPHY_OPENSSL_3xx_OR_GREATER` cfgs derived from those headers, so a
   wrong guess would silently compile the wrong code paths.
3. `openssl-src` vendored builds are not enabled in this release, and enabling
   them means adding `features = ["vendored"]` to `openssl-sys` in the
   workspace `Cargo.toml`. That is a **source modification**.

The milestone instructs: if a source patch becomes necessary, stop and report
the exact patch rather than silently forking. I am doing that instead of
proceeding.

## 7. Two ways forward

**Route 1 - vendor OpenSSL for Android.** Add
`openssl-sys = { version = "0.9.116", features = ["vendored"] }` to
`src/rust/Cargo.toml`. `openssl-src` then cross-compiles OpenSSL from source for
`aarch64-linux-android` using the NDK. Fully reproducible, no header matching,
no dependency on Chaquopy's OpenSSL build, and the resulting wheel is fully
self-contained.

The exact patch is one line. It is **not applied**. Cost: one additional source
modification, a longer build, and a larger `.so`. Trade-off: cryptography would
then use its own OpenSSL rather than sharing Chaquopy's, so there would be two
OpenSSL copies in the process unless the wheel keeps it private.

**Route 2 - match Chaquopy's OpenSSL.** Obtain the exact OpenSSL version and
configuration Chaquopy used, fetch matching headers, and link against
`libcrypto_python.so` / `libssl_python.so` by explicit filename so that
`DT_NEEDED` matches what is already in the APK. This produces a smaller wheel
and shares one OpenSSL, but depends on a Chaquopy-internal detail that is not
published. This is the supply-chain coupling already flagged in M0-008J.

Route 1 is the better engineering trade. It is a product and licensing decision
as well, since vendoring OpenSSL has licence implications for distribution.

## 8. Wheel structure (required, per the M0-008K-FIX lesson)

When this is built, the package must be reconstructed from the **official
wheel's own files**, never hand-written. The M0-008K-FIX milestone showed that a
hand-written `__init__.py` plus a missing `core_schema.py` produced a broken
package that looked fine until it was imported. The same rule applies here:

- take `cryptography/__init__.py` and all Python modules verbatim from the
  official wheel
- take METADATA, LICENSE, `.pyi` stubs and `py.typed` verbatim
- replace **only** the native `.so`

No invented compatibility symbols.

## 9. ELF analysis, packaging, device test

**Not performed.** No `.so` was produced, so there is nothing to analyse, no
wheel to package, and no device test to report. These sections are deliberately
empty rather than speculative.

Required when redone:
architecture, SONAME, `DT_NEEDED` (must be `libpython3.14.so`,
`libcrypto_python.so`, `libssl_python.so`, `libdl.so`, `libc.so`), exported and
undefined symbols, relocation types, `RPATH`/`RUNPATH`, Android API floor, and
the absence of any unresolved runtime dependency.

Also required: delete `app/build/python` and use `--rerun-tasks` before the
device run, and read the installed `.so` hash back **from the device**. The
M0-008J stale-artifact failure and the M0-008K-FIX recovery both came from
skipping that check.

## 10. Negative control

Not applicable in this milestone because nothing was built. The existing probe
still fails loudly on `ImportError`, and the stub-linked `pydantic-core` binary
remains available as a permanent negative control for the loader path.

## 11. Supply-chain hashes

| Artefact | Hash |
|---|---|
| `cryptography-50.0.1.tar.gz` | `5dd9bda1c12b4162f6ff568eeb5e0ff956c28d14406e875cfe8a63a2d414ff20` |
| NDK | r27c |
| Rust | 1.99.0 |
| OpenSSL | **not selected - see section 6** |

No binaries, wheels, APKs, keys or credentials are committed.

## 12. Remaining risks

1. **OpenSSL size.** Even the vendored route adds several MB to the APK, on top
   of `libcrypto_python.so` and `libssl_python.so` which are already present.
2. **Licence.** Vendoring OpenSSL has Apache-2.0 distribution implications that
   the product owner must accept.
3. **Two OpenSSL copies.** Route 1 may leave both the vendored copy and
   Chaquopy's copy resident.
4. **abi3 vs cp314.** The workspace enables pyo3 `abi3`. An abi3 build links
   `libpython3.so`, which does **not** exist on Android; forcing
   `DT_NEEDED libpython3.14.so` via link flags should work because the stable
   ABI is forward compatible, but this must be verified rather than assumed.
5. **Remaining packages.** `Pillow`, `PyNaCl`, `httptools`, `watchfiles`,
   `orjson`, `jiter`, `pillow-heif` and `resvg-py` are all still untested.
   `pillow-heif` and `resvg-py` additionally need third-party native
   libraries and are likely harder than cryptography.

## 13. Next dependency

None should be started yet. `cryptography` must be completed first, because it
is the one package with an external native dependency chain and therefore the
one that most directly tests whether the M0-008K-FIX recipe generalises.

Recommended immediate next step: apply the one-line `vendored` patch described in
section 7 Route 1, after the product owner accepts the licence and size
implications, then rebuild and rerun the full device validation.

## 14. Evidence labels

- Hermes pin and its CVE references: VERIFIED FROM SOURCE
- cryptography build system, pyo3 0.29, openssl-sys 0.9.116 without `vendored`:
  VERIFIED FROM SOURCE
- sdist hash: VERIFIED FROM SOURCE
- Chaquopy's OpenSSL libraries, their symbol counts and Chaquopy's own
  cryptography `DT_NEEDED`: VERIFIED FROM APK
- No OpenSSL headers in the APK: VERIFIED FROM APK
- Feasibility of either route: INFERRED
- Anything about a built cryptography wheel: **not claimed**
