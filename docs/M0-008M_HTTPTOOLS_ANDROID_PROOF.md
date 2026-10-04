> **HISTORICAL RECORD.**
> This document records the state and evidence at the time of its milestone.
> Its historical contents are preserved unchanged, including any conclusion
> later overtaken by newer evidence or by the architecture pivot. Do not read
> it as current architecture guidance.
> Current architecture decisions are governed by `docs/PIVOT-DECISION.md`.

# M0-008M httptools Android Native Proof

## Headline

**A. HTTPTOOLS NATIVE PATH PROVEN**

The native-wheel reconstruction pipeline generalises. `httptools==0.8.0`, an
Android-active Hermes core dependency reached through `uvicorn`, was built for
CPython 3.14 / Android arm64, packaged by Chaquopy 17.0, and executed on the
physical device across four independent native code paths.

This is the first package in the series that did not need any new dependency
sourcing, any host library, or any source patch. It is the cheapest possible
test of whether the pipeline generalises, and it passed.

---

## 1. Hermes dependency evidence

Re-read from the actual revision, not from a cached report:

```
https://raw.githubusercontent.com/NousResearch/hermes-agent/eaecc99c/pyproject.toml
```

```
requires-python = ">=3.11,<3.15"

dependencies = [
  "uvicorn>=0.31.0,<1; python_version >= '3.14'",
  "httptools>=0.6.3,<0.9; python_version >= '3.14'",
  "watchfiles>=0.20,<2; python_version >= '3.14'",
]
```

The surrounding comments in the same file state the intent:

```
# uvicorn's [standard] extra bundles uvloop, httptools, and watchfiles.
# uvicorn pin without [standard] — httptools / watchfiles are now core deps,
```

VERIFIED FROM SOURCE.

- **Android-active:** yes. The marker `python_version >= '3.14'` is satisfied on
  the target interpreter (3.14.0), so the dependency is not dropped.
- **Dependency chain:** Hermes -> `httptools` directly, and also transitively via
  `uvicorn` (uvicorn imports `httptools` for its HTTP protocol implementation).
- **Startup-critical:** yes. Hermes' own comment says the
  `uvicorn>=0.31.0` pin exists because a `gateway/uvicorn` event loop is needed;
  without httptools uvicorn's default HTTP implementation cannot serve requests.
  This is not an optional feature.

## 2. Exact version

Constraint `>=0.6.3,<0.9`. Releases in range: 0.6.3, 0.6.4, 0.7.1, 0.8.0.
The resolver selects the newest, **httptools 0.8.0**, which is the current
release. No pin override exists in the Hermes `[tool.uv]` override section, so
0.8.0 is the correct target.

VERIFIED FROM SOURCE.

## 3. Source provenance

```
source    https://files.pythonhosted.org/.../httptools-0.8.0.tar.gz
bytes     (official PyPI sdist)
LOCAL HASH (sha256, computed locally; the sdist has no published checksum)
          6b2a32f18d97e16e90827d7a819ffa8dbd8cc245fc4e1fa9d1095b54ef4bd999
```

Reference wheel, used verbatim for package contents:

```
httptools-0.8.0-cp314-cp314-manylinux2014_aarch64.manylinux_2_17_aarch64.manylinux_2_28_aarch64.whl
sha256 d76ad7b951387e3632c8716a9bb03ac5b45c5f16119aa409db0459520887944e
```

VERIFIED FROM SOURCE.

## 4. Build configuration

```
build-backend   setuptools.build_meta
sources         httptools/parser/parser.pyx, httptools/parser/url_parser.pyx
extra_compile_args  -O2 -DCYTHON_FREETHREADING_COMPATIBLE=1
build requirement  Cython>=3.1.0
```

Two extensions, each compiling its own bundled parser from C sources:

```
httptools/parser/parser      + vendor/llhttp/src/{api,http,llhttp}.c      -> -lllhttp
httptools/parser/url_parser  + vendor/http-parser/http_parser.c           -> -lhttp_parser
```

VERIFIED FROM SOURCE.

## 5. Third-party libraries

**None external.** Both parsers are vendored in the sdist:

```
vendor/llhttp/        LICENSE, LICENSE-MIT, include/llhttp.h, src/{api,http,llhttp}.c
vendor/http-parser/   LICENSE-MIT, http_parser.c, http_parser.h
```

Both MIT licensed. No system library, no host library, no Termux library, no
OpenSSL. The dependency surface is libc plus the Python C API.

This is the smallest possible dependency surface of any package tested so far.
`cffi` needed libffi; `cryptography` needed OpenSSL; httptools needs nothing.

## 6. Build result

Built inside the existing M0-008L-F image (`m008l/build-env:2`) without
recreating host Python, NDK, Rust or OpenSSL.

```
CC      = <linkwrap>/aarch64-clang
LDSHARED= <linkwrap>/aarch64-clang -shared
CFLAGS  = -fPIC -I<android pydev include> -D__ANDROID_API__=24
LDFLAGS = -L<ndk sysroot>/usr/lib/aarch64-linux-android/24

Created wheel for httptools
Successfully built httptools
```

**First attempt succeeded.** No source patch, no iteration, no version change,
no `--no-deps`, no Linux wheel substitution. VERIFIED BY BUILD.

## 7. ELF analysis

Both rebuilt modules:

```
httptools/parser/parser.cpython-314-aarch64-linux-android.so
httptools/parser/url_parser.cpython-314-aarch64-linux-android.so

ELF 64-bit LSB shared object, ARM aarch64
Class:    ELF64
Machine:  AArch64

DT_NEEDED:
  libpython3.14.so
  libc.so

undefined Py* symbol types
  parser:      222 FUNC,  38 OBJECT,  0 NOTYPE
  url_parser:  200 FUNC,  36 OBJECT,  0 NOTYPE

no libssl / libcrypto / libffi / any host library
no SONAME (normal for a Python extension)
```

VERIFIED BY ELF.

The `0 NOTYPE` figure is the important one. The M0-008L-G linker wrapper
(`-Wl,--no-as-needed -L<pydev lib> -lpython3.14 -Wl,--as-needed`) transferred
without any change, so both modules bound CPython symbols with correct types on
the first try. That is the clearest evidence yet that the pipeline itself, not
per-package luck, is what was fixed.

```
parser      sha256 79f465dbbc78a88dc41b0f44cade3fd0e52740c0d678be68f75e001bd2a97c3f   186128 bytes
url_parser  sha256 7c068305380e49e7a05fc7d4c3b51b1eb7b6f913916670e679e2575ed768384a    99696 bytes
```

## 8. Package integrity

Built by taking the official PyPI aarch64 wheel and replacing only the two
native modules. Every other entry is verbatim: `httptools/__init__.py`,
`_version.py`, `parser/__init__.py`, `errors.py`, `protocol.py`, both `.pyi`
stubs, `py.typed`, `LICENSE`, and `METADATA`.

This repeats the M0-008K-FIX method that fixed the missing-`core_schema.py`
failure: never hand-reconstruct a package.

Module renaming, and the bug found while doing it:

- the official wheel names the modules `*.cpython-314-aarch64-linux-gnu.so`;
  CPython's `EXTENSION_SUFFIXES` on Android does not match that, so the names
  are rewritten to `*.cpython-314-aarch64-linux-android.so`, matching
  Chaquopy's own stdlib naming;
- the first rename attempt silently did nothing because the suffix in the
  filename is `-aarch64-linux-gnu.so` (hyphen), not `.aarch64-linux-gnu.so`
  (dot). This was caught by inspecting the produced archive rather than
  trusting the code path;
- `RECORD` was generated from the files actually written, and verified
  programmatically: `RECORD covers all: True`, `stale: []`. This is the direct
  application of the M0-008L-G lesson, which failed first time on a stale
  RECORD entry.

```
httptools-0.8.0-cp314-cp314-android_24_arm64_v8a.whl
22 entries, Tag: cp314-cp314-android_24_arm64_v8a
```

## 9. Artifact identity

Mandatory chain verified end to end:

```
native build SHA  =  APK embedded SHA  =  device extracted SHA

parser      79f465dbbc78a88dc41b0f44cade3fd0e52740c0d678be68f75e001bd2a97c3f
url_parser  7c068305380e49e7a05fc7d4c3b51b1eb7b6f913916670e679e2575ed768384a
```

Device paths:

```
/data/data/dev.vitngan.probe.chaquopy/files/chaquopy/AssetFinder/requirements/httptools/parser/parser.cpython-314-aarch64-linux-android.so
/data/data/dev.vitngan.probe.chaquopy/files/chaquopy/AssetFinder/requirements/httptools/parser/url_parser.cpython-314-aarch64-linux-android.so
```

`UP-TO-DATE` was not accepted: the entire `app/build` tree was deleted before
the packaging runs, and the APK payload was re-read rather than trusted.

## 10. Physical-device operation

Device: Xiaomi Redmi 24069RA21C, Android 16, API 36, arm64-v8a,
`getconf PAGE_SIZE` = 4096.

```
PYTHON_RUNTIME_OK   3.14.0
HTTPTOOLS_IMPORT_OK  0.8.0
HTTPTOOLS_PARSE_OK   POST
HTTPTOOLS_CHUNKED_OK chunks=2
HTTPTOOLS_NATIVE_OK  parse+chunked+response+url+invalid_rejected
```

VERIFIED ON DEVICE.

Four independent native paths were exercised, covering both rebuilt
extensions and both bundled parsers:

1. **HTTP request parsing** (`parser.so` / llhttp). A `POST` request with
   query string, headers and body parsed to completion. Callbacks fired:
   `on_message_begin`, `on_url`, `on_header`, `on_headers_complete`,
   `on_body`, `on_message_complete`. Verified `url == /v1/messages?x=1`,
   `method == POST` via `get_method()`, `body == hello`.
2. **Chunked transfer decoding** (`parser.so` / llhttp). A chunked body
   produced `chunks == 2` completion callbacks.
3. **HTTP response parsing** (`parser.so`). `HTTP/1.1 204 No Content` parsed to
   completion.
4. **URL parsing** (`url_parser.so` / http-parser). `parse_url` returned
   `scheme=http`, `host=example.invalid`, `port=8080`, `path=/a/b`,
   `query=q=1` — a different extension and a different vendored parser.

Why this is native execution rather than import: the recorded values
(`chunks == 2`, `on_body` receiving exactly `b"hello"`, `port == 8080`) are
produced by llhttp and http-parser running inside the Android `.so` and calling
back into Python. A Python-level fallback cannot produce them.

Instrumented suite: **5 tests, 0 failures** on the device
(`Finished 5 tests on 24069RA21C - 16`, `BUILD SUCCESSFUL`).

## 11. Negative control

A malformed request containing bytes after the header terminator must be
rejected.

Observed on device:

```
HttpParserError: Invalid header token
```

The probe additionally asserts that the completion callback did **not** fire,
so a parser that silently tolerated garbage would fail.

Fail-loud behaviour was exercised repeatedly during this milestone. Four
probe-side defects were caught because the test kept failing rather than
passing quietly:

1. `url_parser.Url` — invented name; the real class is `URL`, with
   `parse_url(bytes)`.
2. `HttpRequestParser()` with no arguments — in 0.8.0 the constructor takes a
   protocol object and callbacks are its methods, not attribute assignments.
3. `on_method` callback — does not exist in 0.8.0; the method is read through
   `get_method()`.
4. Negative control expecting the wrong exception type — httptools rejects the
   bad token itself and never calls the completion callback.

All four were fixed in the probe, and the correct API was read from the shipped
`.pyx`, `.pyi` and upstream `tests/test_parser.py` rather than guessed. No
assertion was weakened.

## 12. Blockers

**None.** httptools is classified **A. HTTPTOOLS NATIVE PATH PROVEN**.

## 13. What generalised, and what did not

**Generalised without modification:**

- the M0-008L-F persistent build image, used as-is;
- the M0-008L-G Android CPython 3.14 development tree;
- the M0-008L-G linker wrapper that produces correctly typed CPython symbols;
- the "official wheel plus replaced native modules" packaging method;
- the module-renaming rule to `.cpython-314-aarch64-linux-android.so`;
- the RECORD regeneration rule;
- the fail-loud probe pattern;
- the artifact identity chain.

**Not applicable this time, because httptools needs none of it:**

- `PYO3_CONFIG_FILE` and `PYO3_CROSS_LIB_DIR` (PyO3 packages only);
- `PKG_CONFIG_PATH` and `CFFI_FORCE_STATIC` (libffi packages only);
- OpenSSL prefix construction (openssl-sys packages only).

**Did not generalise:** nothing was found that failed. This is the first
package in the series where no new problem appeared at all.

## 14. Does this increase confidence in the overall native closure?

Yes, for a specific and limited claim.

All three packages that previously succeeded (`pydantic-core`, `cffi`,
`cryptography`) had something extra: a Rust toolchain, a sourced libffi
artifact, or an OpenSSL prefix. It was therefore reasonable to suspect those
successes depended on per-package work rather than on a working pipeline.
httptools removes that doubt: it is the first package whose only special
requirement was the shared linker wrapper.

What this does **not** establish: `pillow-heif` and `resvg-py` still carry
third-party native libraries, and `Pillow` itself needs image codecs. The
remaining candidates are not all like httptools.

## 15. Next candidate

> **AMENDED BY M0-008N (2026-10-03).** This section recommended `orjson`.
> That was wrong: `orjson` is **not** a Hermes dependency at `eaecc99c`. The
> Rust/PyO3 candidate in the Hermes closure is **`jiter`** (via
> `openai==2.24.0`). See `docs/M0-008N_DEPENDENCY_AUDIT_CORRECTION.md`.

`orjson` and `jiter` are Rust packages with no vendored C, so they would test
the PyO3 path under a different build backend. `PyNaCl` needs libsodium, which
is the closest analogue to the libffi case already solved. `pillow-heif` and
`resvg-py` remain the expected hard cases.
