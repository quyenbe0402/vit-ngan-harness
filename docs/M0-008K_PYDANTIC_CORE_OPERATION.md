# M0-008K-FIX pydantic-core Native Operation

## Headline

**A. PYDANTIC-CORE NATIVE OPERATION PROVEN**

The corrected binary was deployed, imported, and executed a full validation
cycle through the Rust extension on the physical device. No mocking, no
swallowed exceptions.

---

## 1. Reproduction before the fix

M0-008K-FIX reproduced the exact failure before changing anything:

```
ImportError: cannot import name '__pydantic_core_version__'
             from 'pydantic_core._pydantic_core'
```

Binary at that moment, captured on device:

```
sha256 8b2c0feb542299205c165650739f04b3879c88f7bb5e62e8972551daff19d70f
path   /data/user/0/dev.vitngan.probe.chaquopy/files/chaquopy/AssetFinder/
       requirements/pydantic_core/_pydantic_core.so
```

VERIFIED ON DEVICE.

## 2. Root cause: two defects in the probe wheel

The official `pydantic_core-2.46.4` wheel was downloaded from PyPI and
compared. The probe wheel had been reconstructed by hand in M0-008I and was
wrong in two specific ways.

**Defect 1 - invented symbol.** The probe's generated `__init__.py` did:

```python
from ._pydantic_core import __version__, __pydantic_core_version__
```

The real `__init__.py` imports only `__version__`. `__pydantic_core_version__`
was a symbol I invented. VERIFIED FROM SOURCE.

**Defect 2 - missing package file.** The real wheel ships
`pydantic_core/core_schema.py` (155 574 bytes) and `py.typed`. The probe wheel
contained neither, so the package could not work even if `__init__` had been
correct. VERIFIED FROM SOURCE.

This was a reconstruction defect in the probe artifact, not an upstream defect
and not a loader problem. Upstream `pydantic-core` 2.46.4 is fine.

## 3. Fix

The probe wheel was rebuilt from the **verbatim official package files** taken
from the PyPI wheel, with only the native `.so` replaced by the rebuilt Android
arm64 CPython 3.14 library:

```
pydantic_core/__init__.py        <- official, verbatim
pydantic_core/core_schema.py     <- official, verbatim
pydantic_core/_pydantic_core.pyi <- official, verbatim
pydantic_core/py.typed           <- official
pydantic_core/_pydantic_core.so  <- rebuilt for Android arm64 cp314
pydantic_core-2.46.4.dist-info/* <- official METADATA and LICENSE
```

`typing-extensions>=4.12` was added to the probe's pip requirements, because the
real `__init__.py` does `from typing_extensions import Sentinel`. Without it the
official package cannot import on this build.

No upstream source was modified. The corrected wheel:

```
pydantic_core-2.46.4-cp314-cp314-android_24_arm64_v8a.whl   1818 KB
sha256(_pydantic_core.so)  8b2c0feb542299205c165650739f04b3879c88f7bb5e62e8972551daff19d70f
```

## 4. Binary identity, verified end to end

`--rerun-tasks` was used and `app/build/python` was deleted first, because
M0-008J's failure was caused by Gradle treating the requirements task as
`UP-TO-DATE` and shipping a stale APK. The hash was then read back **from the
device**, not from the build directory:

```
device sha256 = 8b2c0feb542299205c165650739f04b3879c88f7bb5e62e8972551daff19d70f
build sha256  = 8b2c0feb542299205c165650739f04b3879c88f7bb5e62e8972551daff19d70f
```

Identical. There is no ambiguity this time. VERIFIED ON DEVICE.

## 5. Physical-device result

Xiaomi Redmi 24069RA21C, Android 16 / API 36, arm64-v8a, Chaquopy 17.0.0,
CPython 3.14.0.

```
APK_INSTALLED            yes
PYTHON_RUNTIME_OK        yes, 3.14.0
NATIVE_IMPORT_OK         yes
  package_version        2.46.4
NATIVE_OPERATION_OK      yes
  SCHEMA_BUILT           int
  VALIDATOR_BUILT        SchemaValidator
  VALIDATE_VALID_42      42 (int)
  INVALID_REJECTED       yes
  error_type             int_parsing
  SERIALIZE              b'42'
  ROUNDTRIP              42
```

## 6. Why this proves native execution

`VALIDATE_VALID_42 = 42 (int)` is not a pass-through. The value came back as a
genuine Python `int` produced by Rust code that parsed the input according to a
Rust-built schema.

`INVALID_REJECTED` with `error_type = int_parsing` is the decisive evidence.
`int_parsing` is a pydantic-core error identifier that exists only in the Rust
implementation. A Python wrapper could not invent it. The validator genuinely
rejected `"not-an-int"` and produced the upstream-defined error code.

`SERIALIZE = b'42'` and `ROUNDTRIP = 42` exercise a second, separate native
subsystem (`SchemaSerializer` and `from_json`).

## 7. Negative control

- The probe returns `NATIVE_OPERATION_RESULT=FAIL` if `validate_python(42)`
  returns anything other than the integer `42`.
- It returns `FAIL` if `validate_python("not-an-int")` does not raise.
- It records `wrong-exception` if the rejection is not a
  `pydantic_core.ValidationError`.
- Nothing is caught and reported as success. Every failure path returns a FAIL
  string that reaches logcat.

The stub-linked binary remains available for re-testing: it fails at load with
`cannot locate symbol "PyLong_Type"`, so the same probe would report
`NATIVE_IMPORT_RESULT=FAIL`. That contrast is the negative control.

## 8. Stop-condition answers

**1. Was the corrected binary actually deployed?**
Yes. Verified by SHA-256 read back from the device after a forced rebuild, with
the build directory cleared and `--rerun-tasks` used. VERIFIED ON DEVICE.

**2. Did native import succeed?**
Yes. `import pydantic_core` succeeds and reports version 2.46.4.
VERIFIED ON DEVICE.

**3. Did SchemaValidator execute successfully?**
Yes. `core_schema.int_schema()` built the schema, `SchemaValidator` was
constructed, and `validate_python(42)` returned `42` as an `int`.
VERIFIED ON DEVICE.

**4. Did invalid input correctly fail?**
Yes. `validate_python("not-an-int")` raised `ValidationError` with
`error_type = int_parsing`. VERIFIED ON DEVICE.

**5. Is the native-wheel reconstruction method now proven for at least one real
Hermes dependency?**
**Yes**, for `pydantic-core` 2.46.4, which is exactly what Hermes pins through
`pydantic==2.13.4` at `eaecc99c`.

This is proven for **one** package only. It does not yet extend to
`cryptography`, `Pillow`, `PyNaCl`, `httptools`, `watchfiles`, `orjson`,
`jiter`, `pillow-heif` or `resvg-py`, none of which were started.

## 9. The reusable recipe

For any pure-Rust or C CPython 3.14 Android extension under Chaquopy:

1. Build from the official PyPI **sdist**, unmodified.
2. Cross-compile with NDK r27c, Rust `aarch64-linux-android`, and
   `PYO3_CROSS=1 PYO3_CROSS_PYTHON_VERSION=3.14`.
3. Link against **Chaquopy's real `libpython3.14.so`**, never a generated stub.
   A stub emits every CPython reference as `STT_NOTYPE` and Android's linker
   refuses to bind them.
4. Wrap with `-Wl,--no-as-needed -lpython3.14 -Wl,--as-needed`.
5. Tag the wheel `cp314-cp314-android_24_arm64_v8a`.
6. Ship the **verbatim upstream package files** plus the rebuilt `.so`. Do not
   hand-write `__init__.py`.
7. Verify the installed `.so` hash from the device after a forced rebuild.

## 10. Remaining unknowns

- Whether the same recipe works for C-only extensions such as `httptools`, which
  should be easier.
- Whether `cryptography` and `PyNaCl` need bundled system libraries.
- Whether `pydantic` 2.13.4 itself now imports on device.
- APK size impact of a full closure.

## 11. Result classification

**A. PYDANTIC-CORE NATIVE OPERATION PROVEN**

Every condition in the classification list is met: hash-verified corrected
binary, hash-verified on device, Python 3.14 running, native import succeeding,
`SchemaValidator` construction succeeding, valid input validating, invalid
input rejected, and no swallowed exception.
