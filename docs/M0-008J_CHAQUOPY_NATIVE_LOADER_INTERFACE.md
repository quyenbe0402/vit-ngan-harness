> **HISTORICAL RECORD.**
> This document records the state and evidence at the time of its milestone.
> Its historical contents are preserved unchanged, including any conclusion
> later overtaken by newer evidence or by the architecture pivot. Do not read
> it as current architecture guidance.
> Current architecture decisions are governed by `docs/PIVOT-DECISION.md`.

# M0-008J Chaquopy Native Loader Interface

## Headline

**B. LOADER CONTRACT PARTIALLY UNDERSTOOD**

The Chaquopy runtime layout and the symbol provider are now fully mapped, and
one concrete defect in the rebuilt wheel was found and fixed. But the native
load **still fails on the device**, so this is explicitly not A.

The remaining failure is now narrow and precisely located: 44
`R_AARCH64_GLOB_DAT` relocations against CPython data symbols.

---

## 1-3. Exact versions

| Item | Value |
|---|---|
| Chaquopy | 17.0.0 (pinned) |
| CPython | 3.14.0 (device-reported) |
| pydantic-core | 2.46.4, from the official PyPI sdist |
| Hermes | `NousResearch/hermes-agent` @ `eaecc99c` |
| Device | Xiaomi Redmi 24069RA21C, Android 16 / API 36, arm64-v8a |

Hermes was re-read: `pydantic==2.13.4`, which pins `pydantic-core==2.46.4`.
The version used is correct and was not changed. VERIFIED FROM SOURCE.

## 4-5. APK native layout

```
lib/arm64-v8a/libpython3.14.so        5670 KB   CPython runtime
lib/arm64-v8a/libchaquopy_java.so      154 KB   JNI bootstrap
lib/arm64-v8a/libsqlite3_python.so     866 KB
lib/arm64-v8a/libcrypto_python.so     3634 KB
lib/arm64-v8a/libssl_python.so         609 KB
lib/arm64-v8a/lib{crypto,ssl,sqlite3}_chaquopy.so   4 KB each

assets/chaquopy/bootstrap-native/arm64-v8a/
    _bz2.cpython-314-aarch64-linux-android.so
    _ctypes.cpython-314-aarch64-linux-android.so
    _lzma.cpython-314-aarch64-linux-android.so
    _random.cpython-314-aarch64-linux-android.so
    _struct.cpython-314-aarch64-linux-android.so
    binascii.cpython-314-aarch64-linux-android.so
    math.cpython-314-aarch64-linux-android.so
    mmap.cpython-314-aarch64-linux-android.so
    zlib.cpython-314-aarch64-linux-android.so
    java/chaquopy.so
```

**Chaquopy uses a real shared CPython, not a static or hidden one.**
`libpython3.14.so` is a genuine ELF shared object with SONAME
`libpython3.14.so`. VERIFIED FROM APK.

Note the extension naming scheme: `<module>.cpython-314-aarch64-linux-android.so`.
This is the EXT_SUFFIX form. VERIFIED FROM APK.

## 6. ELF analysis

### Provider

```
libpython3.14.so   ELF64 AArch64
  SONAME          libpython3.14.so
  DT_NEEDED       libm.so, libdl.so, liblog.so, libc.so
  exported syms   1825   (GLOBAL + DEFAULT visibility)
  PyLong_Type     shndx=22 type=OBJECT(1) bind=GLOBAL(1)
  PyObject_Type   exported
  PyErr_SetString exported
```

### Chaquopy's own working extension, for comparison

```
math.cpython-314-aarch64-linux-android.so
  DT_NEEDED  libm.so, libpython3.14.so, libdl.so, libc.so
  PyLong_Type  shndx=0 type=OBJECT(1) bind=GLOBAL(1)   <- same as ours now
  .rela.dyn  210 entries: 16 GLOB_DAT(0x401), 194 RELATIVE(0x403)
  .rela.plt  79  JUMP_SLOT(0x402)
```

Chaquopy's own extensions declare `DT_NEEDED libpython3.14.so` exactly as we do.
VERIFIED FROM APK. So the "just add DT_NEEDED" theory is confirmed as the
right shape.

### Defect found and fixed in our wheel

Comparing our first rebuild against the provider, **all 162** undefined CPython
symbols had ELF type `0` (STT_NOTYPE) while the provider defines them as
`OBJECT` or `FUNC`:

```
pydantic_core (built against a generated empty stub):
  PyLong_Type      type=0  NOTYPE
  PyErr_SetString  type=0  NOTYPE
  ... 162/162 mismatched

pydantic_core (rebuilt against Chaquopy's real libpython3.14.so):
  PyLong_Type      type=1  OBJECT   correct
  PyErr_SetString  type=2  FUNC     correct
```

**Root cause of the type corruption:** M0-008I linked against a hand-generated
empty stub `libpython3.14.so` built from `void f(void) {}`. Every symbol in that
stub is a function, so the linker recorded the references as functions, and with
`--allow-shlib-undefined` the emitted undefined entries ended up NOTYPE.

**Fix applied:** link against the real `libpython3.14.so` extracted from the
Chaquopy-generated APK. The stub is now **DIAGNOSTIC ONLY** and is not shipped.
VERIFIED BY BUILD.

## 7. CPython symbol visibility

Measured at runtime through `ctypes`, on the physical device:

```
dlsym(RTLD_DEFAULT)                    PyLong_Type       RESOLVED
                                        PyObject_Type     RESOLVED
                                        PyUnicode_Type    RESOLVED
                                        PyErr_SetString   RESOLVED
                                        Py_IsInitialized  RESOLVED
                                        PyModule_Create2  RESOLVED
dlopen("libpython3.14.so", NOLOAD)     handle NON_NULL, all symbols RESOLVED
dlopen("libpython3.14.so", NOLOAD|GLOBAL) handle NON_NULL, all RESOLVED
after forcing RTLD_GLOBAL              all RESOLVED
```

**Every CPython symbol this project needs is globally reachable in the running
process.** VERIFIED ON DEVICE.

This rules out "Chaquopy hides its Python symbols", which was a plausible
theory before measurement.

## 8. dlopen / dlsym results

```
Chaquopy's own _bz2 .so   LOADED   PyInit__bz2 RESOLVED
Chaquopy's own math .so   LOADED   PyInit_math RESOLVED
Chaquopy's own zlib .so   LOADED   PyInit_zlib RESOLVED

our _pydantic_core.so    FAIL (RTLD_LOCAL)  cannot locate symbol "PyLong_Type"
our _pydantic_core.so    FAIL (RTLD_GLOBAL) cannot locate symbol "PyLong_Type"
```

Both our stub-linked and real-libpython-linked builds fail identically.

## 9. Loader order

Python 3.14.0 is initialised before any extension load, and `libpython3.14.so`
is already resident and globally resolvable at the time of the failing
`dlopen`. So this is **not** an ordering problem and **not** an RTLD_GLOBAL
problem. VERIFIED ON DEVICE.

A decisive experiment was run: our `.so` was copied into Chaquopy's own
`bootstrap-native` directory and `dlopen`ed from there. It **still failed**:

```
copied to: .../chaquopy/bootstrap-native/_pydantic_core.cpython-314-aarch64-linux-android.so
dlopen in bootstrap dir -> FAIL cannot locate symbol "PyLong_Type"
```

So file location and namespace entry are not the cause. VERIFIED ON DEVICE.

## 10. Root cause of the PyLong_Type failure

Narrowed to the relocation form. `PyLong_Type` is a **data** symbol. In the
failing library it is the target of an `R_AARCH64_GLOB_DAT` (0x401)
relocation:

```
GLOB_DAT symbols in our .so (44):
  PyLong_Type, PyObject_Type, PyBytes_Type, PyTuple_Type, PyType_Type,
  PyExc_Exception, PyExc_TypeError, PyExc_ValueError, PyExc_RuntimeError,
  PyFloat_Type, PyUnicode_Type, ... PyObject_Free, PyObject_GC_Del,
  _Py_NoneStruct, _Py_TrueStruct, _Py_FalseStruct, _Py_EllipsisObject
```

`math` also uses GLOB_DAT and works, so GLOB_DAT alone is not fatal. What differs
is the set: our library references CPython **data** objects that must be bound
to absolute addresses inside the CPython image at load time. The correct answer
for how Chaquopy's own extensions avoid this is **UNKNOWN** - they may be linked
differently, or Chaquopy may arrange for these to resolve through a mechanism
not visible in the ELF.

INFERRED, not proven: Android's linker will not bind a preemptible
(DEFAULT-visibility) data symbol from a library loaded via `dlopen` outside the
app's class-loader namespace, while function symbols go through the PLT and are
recovered from the global scope.

## 11. Correct linking strategy

What is now established:

1. Chaquopy ships a real `libpython3.14.so`; extensions must and do declare
   `DT_NEEDED libpython3.14.so`. CONFIRMED.
2. The wheel platform tag must be `android_<api>_<abi>`. CONFIRMED.
3. **Link against Chaquopy's real `libpython3.14.so`**, never a generated stub.
   The stub corrupts symbol types. CONFIRMED BY BUILD.
4. Keep `-Wl,--no-as-needed` around `-lpython3.14`, then restore
   `-Wl,--as-needed`. CONFIRMED BY BUILD.
5. Extensions must be named `<module>.cpython-314-aarch64-linux-android.so` to
   match CPython's EXT_SUFFIX. CONFIRMED FROM APK.

What remains open: how to make the GLOB_DAT data-symbol bindings resolve.

The most promising untested route is to let the **Android class loader**
(`System.load`) load the extension rather than libc `dlopen`, because that
places the library in the app's linker namespace where `libpython3.14.so` is
permitted. This is INFERRED and untested.

## 12. Rebuilt wheel details

```
pydantic_core-2.46.4-cp314-cp314-android_24_arm64_v8a.whl   1785 KB
  _pydantic_core.so  ELF64 AArch64, 4.34 MB
  sha256(_pydantic_core.so)
    8b2c0feb542299205c165650739f04b3879c88f7bb5e62e8972551daff19d70f
  Wheel tag  cp314-cp314-android_24_arm64_v8a
```

The wheel is accepted by Chaquopy's pip. VERIFIED BY BUILD.

## 13. Physical-device validation

```
APK_INSTALLED        yes, on 24069RA21C / Android 16 / arm64-v8a
PYTHON_RUNTIME_OK    yes, CPython 3.14.0
NATIVE_IMPORT_OK     NO  - dlopen failed: cannot locate symbol "PyLong_Type"
NATIVE_OPERATION_OK  NO
```

Chaquopy pip installation, APK packaging and installation all work. The load
step does not.

## 14. Negative control

The probe **throws** `IllegalStateException` when the report contains
`IMPORT=FAIL` or lacks `NATIVE_OP=OK`. In the JUnit XML the native-operation
test is recorded as a **failure** with the underlying `ImportError` text, not as
a pass. VERIFIED ON DEVICE.

This matters because in M0-008I an earlier revision of the probe swallowed the
`ImportError` and reported green while proving nothing. That defect was fixed
before any conclusion here was drawn, and the negative control now demonstrates
the test is not capable of reporting a false pass.

## 15. Security and supply-chain implications

- Link-time reference to Chaquopy's `libpython3.14.so` uses a file extracted
  from a locally built APK. Nothing is downloaded from an untrusted source.
- The generated stub is **not** shipped and must not be: it produced a
  structurally invalid library.
- Toolchain pinned: NDK r27c, Rust 1.99.0, PyO3 cross version 3.14,
  `ubuntu:24.04`.
- `pydantic-core` was built from the official PyPI sdist. No source patching.
  Hermes was not modified.
- No credentials, keys or tokens in this milestone.

Residual risk to flag: producing these wheels in CI requires access to a
Chaquopy-built `libpython3.14.so`. That is a Chaquopy-proprietary artifact, so
the wheel supply chain would depend on it. That is a distribution concern for
the product owner, not a technical blocker.

## 16. Remaining closure work

Per instruction, no other native package was started. Outstanding once the
loader contract is closed: `cryptography`, `orjson`, `jiter`, `httptools`,
`watchfiles`, `Pillow`, `PyNaCl`, `pillow-heif`, `resvg-py`.

`firecrawl-anydoc==0.2.4` remains classified as **rebuildable from source**
(abi3, sdist present). The M0-008I correction stands: it is not absent from
PyPI.

## 17. Stop-condition answers

**1. Where do CPython 3.14 C-API symbols live in Chaquopy 17.0?**
In `lib/arm64-v8a/libpython3.14.so`, a real ELF shared object, SONAME
`libpython3.14.so`, exporting 1825 GLOBAL/DEFAULT symbols including
`PyLong_Type`. VERIFIED FROM APK and ON DEVICE.

**2. Why did PyLong_Type fail?**
Because it is a **data** symbol requiring an `R_AARCH64_GLOB_DAT` binding at
load time, not a PLT/function binding. 44 such CPython data relocations are
present. A contributing defect, now fixed, was that linking against an empty
stub gave every reference the wrong ELF type (NOTYPE instead of OBJECT/FUNC).
The stub defect alone was not sufficient to make it load. VERIFIED BY BUILD and
ON DEVICE; the precise linker rule remains INFERRED.

**3. What exact loader/linker contract is required?**
Known: real `libpython3.14.so` in the APK; `DT_NEEDED libpython3.14.so` on the
extension; correct `android_<api>_<abi>` wheel tag; correct
`.cpython-314-aarch64-linux-android.so` filename; symbols linked against the
real Chaquopy CPython so their ELF types are correct; `libpython3.14.so` already
resident and globally resolvable before the extension loads. Not yet known: how
the GLOB_DAT data bindings must be arranged.

**4. Can pydantic-core 2.46.4 satisfy that contract?**
**Partially.** It builds, packages, installs and is accepted by Chaquopy's pip.
The symbol-type defect is fixed. The GLOB_DAT binding is unsolved, so no.

**5. Does native code execute successfully on the physical device?**
**No.** CPython executes; the rebuilt extension does not load.

**6. Is Hermes dependency reconstruction still technically alive?**
**Yes.** The native ABI is fundamentally sound. Both defects found so far were
toolchain or linking issues, not capability limits. A rebuilt, correctly linked
Rust/PyO3 extension for Android arm64 CPython 3.14 is now demonstrated up to
the load step. But it is still unproven end to end, so this is an
**honest "alive but unproven"**, not a green light.

## 18. Classification

**B. LOADER CONTRACT PARTIALLY UNDERSTOOD**

Not A: the mechanism is not fully identified, the rebuild does not load, and
native operation does not execute.
Not C: substantial progress was made and a concrete defect was fixed.

## Recommended next experiment

Smallest falsifiable step: load the rebuilt extension through the **Android
class loader** (`System.load`) instead of libc `dlopen`, so that it enters the
app's linker namespace. If GLOB_DAT bindings then resolve, the loader contract
is closed. If they still do not, the remaining difference is inside
`libpython3.14.so` itself and the next step is to compare the symbol-binding
metadata of Chaquopy's own extensions that reference CPython data objects.

Do **not** fan out to the other native packages until this one loads.
