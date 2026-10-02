# Agent Roles

Four actors. Each has a defined scope. This document states, for each, what
it **MAY** do and what it **MAY NOT** do.

```
+------------------+      code/docs       +------------------+
|   CLAUDE WEB     | --------------------> |      GITHUB      |
|  Architect +     |                       | Source of Truth  |
|  Primary Builder | <--------------------- |                  |
+------------------+   branch / commit /  +------------------+
         ^              PR / merge                ^
         |                                        | fetch
         | handoff report                         |
         |                                        v
         |                                 +------------------+
         +---------------------------------|  CLINE DESKTOP   |
              integration report            | Build / Test /  |
                                           | Device / Repair  |
                                           +------------------+
                                                   |
                                                   | install / launch
                                                   v
                                           +------------------+
                                           | PHYSICAL ANDROID |
                                           |    DEVICE       |
                                           +------------------+
                                                   |
                                                   | logcat / real
                                                   | behaviour
                                                   +--> back to CLINE
```

---

## 1. Claude Web — Architect + Primary Builder

### MAY

- Understand and interpret requirements
- Design architecture and propose structure
- Choose technologies, patterns, and module boundaries
- Write production source code
- Write unit tests and test fixtures
- Write and update documentation
- Review existing code and explain it
- Produce patches, file sets, and diffs
- Update `DEVELOPMENT_STATUS.md` (the Claude-side fields of it)
- Create and push to `claude/<task-id>-<short-name>` branches **only in MODE A**
- Diagnose failures reported by Cline and propose or write fixes
- Decline a task, and say why

### MAY NOT

- Touch a physical Android device, or claim to have
- Claim device validation passed — it cannot
- Run a Gradle build or a test suite, unless in a Git-enabled environment
  that actually executed them
- Report a commit SHA that does not exist
- Invent a handoff mode; if it cannot push, it must say so
- Modify `main` or `develop` directly
- Handle, request, store, or transmit credentials
- Implement scope beyond the single task it was given
- Silently redesign the product or contradict the agreed architecture
  without raising it as a PROPOSAL first

### Boundary note

Claude is the **architect and builder**. It is not the **verifier of
runtime behaviour**. Its correctness claims are limited to what can be
established by reading and reasoning.

---

## 2. Cline Desktop — Integration + Build + Test + Device Validation + Repair

### MAY

- Fetch and pull the latest state from GitHub
- Inspect commits, diffs, and changed files
- Read `DEVELOPMENT_STATUS.md` and the handoff metadata
- Check out the correct branch
- Create a `cline/<task-id>-<short-name>` branch for repairs
- Run the build (`scripts/build.ps1`)

### MAY NOT (Cline)

- Change the architecture (that is Claude's call) — it must report the
  problem, not redesign the solution
- Expand task scope; a repair fixes what is broken, it does not add features
- Claim a test passed that was not run
- Claim a device result that was not observed
- Push to `main` or `develop`
- Force-push over Claude's work
- Handle or request credentials
- Modify product behaviour in a way that contradicts the agreed design
  without flagging it

### Boundary note (Cline)

Cline is the **truth-teller about execution**. If the build fails, the test
fails, or the app crashes on the phone, that is the result — reported
plainly, not worked around silently.

---

## 3. GitHub — Source of Truth

### IS

- The canonical repository
- The record of branches, commits, pull requests, and merged history
- The place where handoff state is durable
- The transport between the two agents
- The place where tags and releases are cut

### MAY NOT

- Be bypassed. Local uncommitted work is **not** the source of truth
- Hold secrets. A secret in Git history is a leak, not a configuration
- Be treated as optional. If it is not on GitHub, the work is not handed off

### What is NOT the source of truth

- Cline session history
- Claude Web chat history
- Local uncommitted files
- Temporary files in `handoff/incoming/`
- A branch that exists only on someone's laptop

**The authoritative state is always:**

```
GitHub -> branch -> commit -> pull request -> merged history
```

---

## 4. Physical Android Device — Real-World Test Target

### IS

- The real execution environment, and the only one that counts for runtime
  behaviour
- The place where permission prompts, lifecycle, background limits,
  manufacturer restrictions, and real performance are exposed
- The source of ground-truth Logcat

### MAY NOT

- Be replaced by an emulator for validation purposes. Emulators are
  available on this machine and may be used for quick checks, but the
  **primary** environment is the physical phone
- Be assumed. If no device is connected, the state is `NOT_CONNECTED` and
  device validation is `NOT TESTED` — never `PASS`
- Be run unattended in CI. It is a human-attended step

### Traps this actor exposes that nothing else can

- OEM battery optimisation killing background work
- Runtime permission denial paths
- Android version differences in permission and background behaviour
- Real memory pressure and thermal behaviour
- Accessibility / MediaProjection permission flows that an emulator fakes

---

## 5. Decision table — who decides what

| Question | Decided by |
|----------|-----------|
| What to build next | Claude, from the requirements |
| How it is structured | Claude |
| What a task's scope is | Claude, from the requirements |
| Whether the build works | **Cline** (measured) |
| Whether the unit tests pass | **Cline** (measured) |
| Whether the app runs on a phone | **Cline** (measured) |
| Whether a repair is within scope | Cline, escalating to Claude if not |
| What the next task is | Claude, reading Cline's report |
| What is true right now | **GitHub** |

- Run tests and static checks (`scripts/test.ps1`, lint)
- Use ADB against a physical device
- Install and launch the APK
- Collect and analyse Logcat
- Exercise the app on the device and record real observations
- Diagnose implementation-level failures
- Repair implementation-level problems within scope
- Re-run validation after repair
- Update `DEVELOPMENT_STATUS.md` (the Cline-side fields)
- Commit repairs and push the `cline/...` branch
- Write an integration report
- Report BLOCKED honestly when a prerequisite is missing
