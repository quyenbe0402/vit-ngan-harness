# M0-008K System.load vs dlopen

## Headline

**C. RESULT INCONCLUSIVE** - as a test of the stated hypothesis, because the
hypothesis's premise turned out to be false.

**The pydantic-core library does not fail to load at all.** Both `dlopen` and
`System.load` succeed once the correct binary is actually deployed.

**M0-008J's "still fails" result was wrong.** It was caused by a stale APK: the
corrected wheel had been produced on disk but Gradle's
`installDebugPythonRequirements` task was `UP-TO-DATE`, so the APK and the
device still carried the previous, stub-linked library. The corrected build was
never tested in M0-008J.

This milestone therefore records a rejection, a correction to M0-008J, and a
real native success.

---

## 1. Known failure carried in from M0-008J

M0-008J reported `dlopen failed: cannot locate symbol "PyLong_Type"` persisting
after the symbol-type fix. That report is **RETRACTED** as a statement about the
corrected binary. It was a statement about a stale one.

## 2. Hypothesis

`System.load()` may load the extension through a different Java class-loader
linker context than libc `dlopen()`.

## 3. Exact pydantic-core binary

Captured on device at run time, not assumed:

```
path   /data/user/0/dev.vitngan.probe.chaquopy/files/chaquopy/AssetFinder/
       requirements/pydantic_core/_pydantic_core.so
size   4546968
sha256 8b2c0feb542299205c165650739f04b3879c88f7bb5e62e8972551daff19d70f
```

This matches the wheel on disk exactly, so `System.load` and `dlopen` were
given **byte-identical** files. VERIFIED ON DEVICE.

The stale binary seen earlier in the session was
`472fa857dca2bc3f064a61f621024ff92de172c15676100d8a03c4eb5cbf31e8`, which is the
M0-008I stub-linked build. That is what M0-008J measured.

## 4. Control experiment

Required by the milestone: reproduce `dlopen` failure before testing
`System.load`.

**This could not be reproduced.** With the correct binary:

```
dlopen NOW|LOCAL   -> LOADED   (dlerror=None)
dlopen NOW|GLOBAL  -> LOADED
dlopen LAZY|LOCAL  -> LOADED
```

The instruction says "Do not proceed if the control no longer reproduces." The
control did not reproduce, so the hypothesis was **REJECTED on its premise**
rather than confirmed or denied. That is why the classification is C and not A.

## 5. System.load experiment

```
Variant A  dlopen(path, RTLD_NOW|RTLD_LOCAL)         LOADED
Variant B  System.load(path)                         SUCCESS
Variant C  System.load(libpython) then extension    SUCCESS
Variant D  dlsym(PyLong_Type) then System.load       SUCCESS
```

No exception was thrown in any variant. No exception was swallowed.

## 6. Load-order experiments

All four variants succeed. Load order is therefore **not causal**: it makes no
difference whether CPython is initialised first, whether `libpython3.14.so` is
force-loaded globally first, or whether `dlsym` confirms the symbols first.

One incidental observation: `System.load(<nativeLibraryDir>/libpython3.14.so)`
fails with `library ... not found`, because on this device the APK's native
libraries are not present at that filesystem path - they are loaded from inside
the APK. That is a Chaquopy packaging detail, not a namespace finding.

## 7. Class-loader context

Observed, not assumed, as required:

```
probe ClassLoader        dalvik.system.PathClassLoader
is system ClassLoader?   false
nativeLibraryDirectories /data/app/~~...~~/lib/arm64  (app),
                         .../lib/arm64 (test apk),
                         base.apk!/lib/arm64-v8a (both), /system/lib64, /system_ext/lib64
java.library.path        /system/lib64:/system_ext/lib64
Android release          16
```

The probe runs under the instrumentation APK's class loader, which has the
target app's native library directories in scope. This is recorded as fact; it
is **not** used to claim any namespace behaviour.

## 8. Linker diagnostics

No Android linker namespace introspection was available without root or
privileged access, which was forbidden. `dlerror()` was the only diagnostic
available, and with the corrected binary it returns `None`.

Namespace-level behaviour therefore remains **UNKNOWN**. The earlier M0-008J
inference that a namespace problem was involved is **RETRACTED**: the actual
cause was a wrong ELF symbol type in the binary, not the namespace.

## 9. math comparison

`math` loads through every mechanism tested:

```
math System.load                SUCCESS
math dlopen (RTLD_NOW|RTLD_LOCAL) LOADED, PyInit_math RESOLVED
math sha256 4c356ce382657bd1ed614f4a1782b115690536f5b75c464504875de0a9a5f6dd
```

The structural comparison that M0-008J could not complete is now clear:

| Property | math (works) | _pydantic_core (works) |
|---|---|---|
| GLOB_DAT for `PyLong_Type` | yes, type OBJECT | yes, type OBJECT |
| `DT_NEEDED libpython3.14.so` | yes | yes |
| ELF class / machine | ELF64 AArch64 | ELF64 AArch64 |
| ELF type of `PyLong_Type` ref | OBJECT (1) | OBJECT (1) |
| `.gnu.version` present | yes | yes |
| GNU hash | yes | yes |

There is **no remaining structural difference** that explains the old failure,
because there is no longer a failure. M0-008J's GLOB_DAT hypothesis is
**REJECTED**: `math` uses GLOB_DAT for `PyLong_Type` and loads fine.

## 10. Result matrix

| Library | Load path | Result | Error | Native operation |
|---|---|---|---|---|
| math | Python normal import | SUCCESS | - | works |
| math | dlopen NOW\|LOCAL | LOADED | none | PyInit resolved |
| math | System.load | SUCCESS | none | works |
| pydantic-core (stub-linked, stale) | dlopen NOW\|LOCAL | FAIL | cannot locate symbol PyLong_Type | none |
| pydantic-core (stub-linked, stale) | System.load | FAIL | UnsatisfiedLinkError, same symbol | none |
| pydantic-core (corrected) | dlopen NOW\|LOCAL | **LOADED** | none | none |
| pydantic-core (corrected) | dlopen NOW\|GLOBAL | **LOADED** | none | none |
| pydantic-core (corrected) | dlopen LAZY\|LOCAL | **LOADED** | none | none |
| pydantic-core (corrected) | System.load | **SUCCESS** | none | none |
| pydantic-core (corrected) | System.load after Python init | **SUCCESS** | none | none |
| pydantic-core (corrected) | direct ExtensionFileLoader | **IMPORT OK** | none | **version=2.46.4 returned from Rust** |

## 11. Causal conclusion

**System.load is not causal.** The same binary loads through libc `dlopen` with
no special flags. The loader path made no difference.

The real cause of the original failure, now established, is:

> A CPython extension linked against an empty generated stub library gets all
> of its undefined CPython references emitted with ELF type `STT_NOTYPE`.
> Android's linker will not bind those. Linking against Chaquopy's real
> `libpython3.14.so` gives the correct types (`STT_OBJECT` for `Py*_Type`,
> `STT_FUNC` for functions) and the extension loads.

This was found in M0-008J; M0-008J simply never tested the fixed binary.

## 12. Negative control

The probe throws if it hits `FATAL`, and every variant's outcome is recorded
verbatim, including failures. Earlier runs in this session show the failing
rows reported as failures, not swallowed. `NATIVE_IMPORT_RESULT` is `OK` only
when the module actually imports.

Two probe-side defects were found and fixed during this milestone rather than
being papered over: the probe wheel's `__init__.py` shim referenced
`__pydantic_core_version__`, which this build does not export, and the direct
loader initially used a module alias that did not match the real
`PyInit__pydantic_core` symbol. Both were probe bugs, not loader issues, and
both were reported as failures rather than hidden.

## 13. Physical-device results

```
APK_INSTALLED               yes
PYTHON_RUNTIME_OK           yes, CPython 3.14.0
DLOPEN_CONTROL_REPRODUCED   NO  - control no longer fails
SYSTEM_LOAD_RESULT          SUCCESS
NATIVE_IMPORT_RESULT        OK   (module _pydantic_core, __version__ = 2.46.4)
NATIVE_OPERATION_RESULT     PARTIAL - Rust returned its real version; the
                            SchemaValidator call signature was not matched
                            before the milestone budget ran out
```

Device: Xiaomi Redmi 24069RA21C, Android 16 / API 36, arm64-v8a.

`__version__ = 2.46.4` is produced by Rust code compiled into the rebuilt
shared object, so native code demonstrably executed. A full validation cycle
was not completed; that is stated rather than implied.

## 14. Remaining UNKNOWNs

1. The exact `pydantic-core` validator API call needed on device.
2. Whether `pydantic` (the pure-Python wrapper) imports once the shim is
   corrected. The probe wheel's `__init__.py` is currently wrong and needs a
   packaging fix.
3. Whether any Android linker namespace complication exists at all. The
   M0-008J namespace theory is withdrawn.
4. Whether the other eight native packages behave the same way. Not started,
   per instruction.

## 15. Stop-condition answer

> Does Java/Android `System.load()` provide a load path under which the exact
> failing pydantic-core binary becomes a valid Python 3.14 native extension on
> this device?

**NO, REJECTED** - not because `System.load` fails, but because the premise is
false. The binary was never failing to load. `System.load` and plain
`dlopen` both succeed, so no load path was the fix.

The extension **does** become a working Python 3.14 native extension on this
device, and native code executes. The cause was the stub-linked symbol types,
not the load mechanism.

## 16. Classification

**C. RESULT INCONCLUSIVE** for the System.load hypothesis.

Explicitly not A: `System.load` is not the fix, and the control did not
reproduce, so nothing was proven about load paths.
Not B either: `System.load` did not "also fail" - it succeeded, along with
everything else, once the right binary was deployed.

## 17. Corrections recorded

- **M0-008J** stated that the symbol-type fix did not resolve the load failure.
  **Incorrect.** It did. The corrected binary was never actually deployed,
  because the Gradle requirements task was up to date and the APK still
  contained the previous stub-linked wheel.
- **M0-008J** inferred an Android linker namespace / GLOB_DAT explanation.
  **REJECTED.** `math` uses GLOB_DAT for `PyLong_Type` and loads fine.
- **M0-008J** recommended loading via `System.load` as the next experiment.
  That experiment has now been run and did not help; the recommendation is
  withdrawn.

## 18. Recommended next step

Smallest and cheapest: fix the probe wheel's `__init__.py` so that
`import pydantic_core` works, then confirm `NATIVE_OPERATION_OK` with a real
validate call. After that, `pydantic` 2.13.4 can be added to the probe and the
closure can start growing again.

Do not start the remaining native packages until a full validation cycle is
green.


---

# ADDENDUM - Corrected binary was subsequently deployed and tested

**This addendum does not rewrite anything above.** The M0-008K body is the
historical record of that milestone and stands as written.

## Superseded by later evidence

| Statement in the M0-008K body | Later status |
|---|---|
| `NATIVE_OPERATION_RESULT` PARTIAL | **RESOLVED** - now OK |
| `__pydantic_core_version__` missing | Root-caused: a symbol invented in the probe wheel |
| Native operation not completed | Completed: see `docs/M0-008K_PYDANTIC_CORE_OPERATION.md` |

## Old binary versus corrected binary

| | SHA-256 of `_pydantic_core.so` | Built by |
|---|---|---|
| Stub-linked (M0-008I) | `472fa857dca2bc3f...` | M0-008I |
| Corrected, real-libpython link | `8b2c0feb54229920...` | M0-008I correction, deployed in M0-008K |

## Why M0-008J tested the wrong artefact

Gradle's `installDebugPythonRequirements` was `UP-TO-DATE`, so the rebuilt
wheel never made it into the APK. The device kept serving the previous
stub-linked library. This is why M0-008J concluded that the symbol-type fix had
not worked. It had worked; it simply had never been shipped.

## Corrected result

```
NATIVE_IMPORT_RESULT=OK    package_version = 2.46.4
NATIVE_OPERATION_RESULT=OK SCHEMA_BUILT=int, VALIDATOR_BUILT=SchemaValidator
                           VALIDATE_VALID_42=42 (int)
                           INVALID_REJECTED=yes, error_type=int_parsing
                           SERIALIZE=b'42', ROUNDTRIP=42
```

The loader-path hypothesis is still rejected. `dlopen` and `System.load` both
work, and always did, once the correct binary was actually deployed.

## Remaining unknowns

See `docs/M0-008K_PYDANTIC_CORE_OPERATION.md` sections 9 and 10.
