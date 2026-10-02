# GitHub Actions — future CI structure

This directory is **intentionally empty of workflow files right now**.

## Why there is no CI YAML yet

The repository currently contains **development infrastructure only** — no
Gradle project, no Android application module, no test sources. A workflow
YAML file that references `./gradlew` or a Java/Gradle setup would fail on
every run, which would be worse than having no CI at all: it would produce
red checks that nobody trusts.

CI is therefore **prepared but not enabled**. The structure is ready so that
adding CI later is a small, well-understood change.

## Planned CI stages

When the Android product project exists, CI will run these stages in order.
Each stage is a separate job that consumes the artifact of the previous one.

| # | Stage | What it does | Likely tool | Status |
|---|-------|--------------|-------------|--------|
| 1 | `lint` | Static analysis / style / correctness rules | `gradlew lint`, ktlint, detekt | PLANNED |
| 2 | `unit-test` | JVM unit tests (`testDebugUnitTest`) | `gradlew test` | PLANNED |
| 3 | `build` | Compile debug (and later release) variants | `gradlew assembleDebug` | PLANNED |
| 4 | `integration-test` | Instrumentation / connected tests | `gradlew connectedDebugAndroidTest` on a GitHub-hosted emulator | PLANNED |
| 5 | `artifact-validation` | Verify APK exists, is non-empty, passes `aapt dump badging`; upload as artifact | `actions/upload-artifact` | PLANNED |

## Rules for adding CI

1. **Never** commit a workflow that cannot pass. If the Android project does
   not exist, CI stays off.
2. Every stage must be independently reproducible from a clean checkout.
3. CI must never receive device-unique credentials. It uses GitHub's own
   ephemeral secrets. See `docs/SECRETS_POLICY.md`.
4. Physical-device validation is **never** done in CI. A real phone is the
   ground truth and is exercised by Cline locally.
5. CI is a *filter*, not a replacement for the local loop. A green CI run
   does not mean the app works on the user's phone.

## Enabling CI later — checklist

- [ ] Android Gradle project exists at the repository root
- [ ] `gradlew.bat` and `gradlew` are committed and executable
- [ ] `gradle/wrapper/gradle-wrapper.jar` is committed
- [ ] `scripts/build.ps1` (or an equivalent POSIX script) succeeds locally
- [ ] `scripts/test.ps1` succeeds locally
- [ ] A `ci.yml` is added to this directory with stages 1-3 enabled first
- [ ] Stages 4-5 are added only after stage 3 is green and stable
- [ ] Branch protection is enabled on `main` requiring this check

