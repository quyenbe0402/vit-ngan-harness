# Chaquopy Closure Probe - NON-PRODUCTION (M0-008H / M0-008I)

Throwaway feasibility artifacts. NOT part of the production app. Never ship.

## M0-008H result: dependency closure FAILS

Chaquopy 17.0.0 + Python 3.14 + arm64-v8a cannot install the Hermes @ eaecc99c
closure. See docs/M0-008H_CHAQUOPY_DEPENDENCY_CLOSURE.md.

## M0-008I result: wheels ARE rebuildable

`probes/native-wheels/pydantic_core-2.46.4-cp314-cp314-android_24_arm64_v8a.whl`
was cross-compiled from the official PyPI sdist using NDK r27c + Rust 1.99 and
is accepted by Chaquopy's pip. See
docs/M0-008I_NATIVE_WHEEL_RECONSTRUCTION.md.

It builds, packages, installs and reaches the device, but `dlopen` fails with
`cannot locate symbol "PyLong_Type"`. The probe throws on that state so it can
never report a false pass.

The rebuilt wheel is a large binary artifact and is NOT committed. Rebuild it
with the documented NDK/Rust/stub-library procedure instead.

### Android linking requirements that are not in Chaquopy's docs

1. Android ships no `libpython3.X.so`. Link against a generated empty stub with
   the correct `-soname`.
2. Android has no standalone `libunwind.so` since API 24; stub it too.
3. `--as-needed` drops the Python library unless you pass `-Wl,--no-as-needed`
   followed by `-lpython3.X` and then `-Wl,--as-needed`. Skipping this yields a
   `.so` with no Python DT_NEEDED and a confusing runtime symbol error.
4. The wheel platform tag must be `android_<api>_<abi>`, e.g.
   `android_24_arm64_v8a`. `linux_aarch64` is rejected by pip.
