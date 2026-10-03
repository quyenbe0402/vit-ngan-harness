# Chaquopy Closure Probe - NON-PRODUCTION (M0-008H)

Throwaway feasibility artifact. NOT part of the production app. Never ship.

## Result: BUILD FAILS - closure blockers identified

Chaquopy 17.0.0 + Python 3.14 + arm64-v8a. The Hermes @ eaecc99c closure
cannot be built. See `docs/M0-008H_CHAQUOPY_DEPENDENCY_CLOSURE.md`.

Verified blockers (pip --only-binary=:all: --platform android_34_arm64_v8a):

| Dependency | Reason |
|---|---|
| firecrawl-anydoc==0.2.4 | not on PyPI at all |
| cryptography==50.0.1 | no android wheel |
| httptools | no android wheel |
| watchfiles | no android wheel |
| Pillow==12.3.0 | no android wheel |
| pynacl | no android wheel |
| jiter (via openai) | no android wheel |
| pydantic-core (via pydantic) | no android wheel |
| pillow-heif | absent from Chaquopy native index |
| resvg-py | absent from Chaquopy native index |

Structural blocker: Chaquopy's native repository (chaquo.com/pypi-13.1) has
**no cp314 Android wheels at all** - shipped ABI tags stop at cp313.

The `install(...)` lines for known-blocked packages were removed so the probe
documents the *remaining* resolution, not a patched fake-green closure. No
package was modified or vendored to force success.
