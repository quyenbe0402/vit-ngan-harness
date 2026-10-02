# Development Loop

The canonical loop. Every task goes around this circle exactly once.

```
                         +---------------------------+
                         |       CLAUDE WEB          |
                         |  Architect + Builder      |
                         +-------------+-------------+
                                       |
                                       v
                                 IMPLEMENTATION
                                       |
                                       v
                         +---------------------------+
                         |          GITHUB           |
                         |     Source of Truth       |
                         +-------------+-------------+
                                       |
                                       v  (fetch)
                         +---------------------------+
                         |      CLINE DESKTOP        |
                         |  Build / Test / Diagnose  |
                         +-------------+-------------+
                                       |
                                       v
                                    BUILD
                                       |
                                       v
                                     TEST
                                       |
                                       v
                                     ADB
                                       |
                                       v
                         +---------------------------+
                         |    PHYSICAL ANDROID       |
                         |       DEVICE              |
                         +-------------+-------------+
                                       |
                                       v
                                 REAL RESULT
                                (pass or fail)
                                       |
                                       v
                                CLINE REPAIR
                                       |
                                       v
                         +---------------------------+
                         |          GITHUB           |
                         |   commit + push repair    |
                         +-------------+-------------+
                                       |
                                       v
                                 CLAUDE WEB
                                  reads result
                                       |
                                       v
                                 NEXT TASK
                                       |
                                       +-------> back to the top
```

**The repository is the shared state between the two agents.** Neither
agent holds private truth. Everything that matters is on GitHub, committed.

---

## What each arrow means in practice

| Arrow | Mechanism | Verified by |
|-------|-----------|-------------|
| Claude → Implementation | Claude writes code and tests | The diff |
| Implementation → GitHub | `git commit`, `git push` | Branch exists on the remote |
| GitHub → Cline | `.\scripts\git-fetch-all.ps1` | Remote branch is visible locally |
| Cline → Build | `.\scripts\build.ps1` | Exit code, artifact present |
| Build → Test | `.\scripts\test.ps1` | Pass/fail counts |
| Test → ADB | `adb devices` | A serial in state `device` |
| ADB → Device | `.\scripts\install-device.ps1` | Install succeeds |
| Device → Real result | Launch + manual use + Logcat | Observed behaviour |
| Real result → Repair | Diagnosis from the evidence | Root cause identified |
| Repair → GitHub | `cline/...` branch committed and pushed | Branch exists |
| GitHub → Claude | Claude reads the report | Report is complete |

---

## Why the loop is shaped this way

**Why GitHub sits in the middle, twice.** The first time it receives
implementation. The second time it receives the repair. Putting a durable,
inspectable boundary on both sides means neither agent can silently change
what the other one is working from.

**Why the physical device is mandatory.** Build success and passing unit

---

## Failure paths

The loop has no happy path only. These are all valid outcomes:

| Situation | Loop behaviour |
|-----------|----------------|
| Build fails | Cline reports FAIL, hands back to Claude with the compiler output |
| Test fails | Cline diagnoses; repairs if in scope; otherwise returns to Claude |
| No device connected | Device stages report `NOT_CONNECTED` / `NOT TESTED`; loop continues to report, not to PASS |
| App crashes on launch | Logcat captured; Cline repairs or escalates |
| Architecture problem found | Cline reports it and stops. It does not repair it. |
| Push blocked (no auth) | Cline commits locally, reports BLOCKED, does not fake a push |

**A blocked or failed loop iteration is a valid, complete iteration.** What
is not acceptable is a loop iteration that reports success it did not
observe.

---

## Cycle time expectations

The loop is intentionally serial. There is one Claude task at a time and
one Cline validation at a time. The purpose is a reliable, inspectable
history — not throughput.

Parallelising would mean multiple in-flight tasks with no defined merge
order, which makes the source of truth ambiguous. That is precisely the
problem this workflow exists to prevent.

tests both say nothing about whether the app works when a real user holds a
real phone. OEM background restrictions, runtime permission denials, and
real memory pressure are invisible to both a compiler and an emulator. The
device is the only actor that observes the thing the user actually cares
about.

**Why repair comes after the real result, not before.** Repairing before
the failure is reproduced is guessing. The loop deliberately forces
reproduction first.

**Why the loop returns to Claude rather than continuing.** Repair is
bounded: fix what is broken, do not redesign. When a problem turns out to be
architectural, the loop exits back to Claude with a finding, because only
Claude changes the design.
