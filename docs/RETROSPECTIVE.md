# Retrospective - how M0 reached the wrong critical path

Related: `docs/PIVOT-DECISION.md` (the frozen decision this retrospective explains
the approach to).

---

## What happened

Milestones M0-007B through M0-008O went deep into one question: **can Hermes run
on the Android device?**

That question consumed roughly twenty milestones. The work inside it was real -
five native Python dependencies were built from source for Android arm64,
packaged, installed on a physical device, and executed with full artifact
identity:

- `pydantic-core 2.46.4`
- `cffi 2.1.1`
- `cryptography 50.0.1`
- `httptools 0.8.0`
- `jiter 0.17.0`

What was never established, across those twenty milestones, was the question that
would have determined whether any of it was necessary: **whether the product has
a hard requirement to run offline at all.**

The pivot came from an answer to a product question that could have been asked at
the start. The answer is recorded in `PIVOT-DECISION.md`.

---

## Process failure

**The gate ordering in the master spec was not followed.** The spec set out an
order in which repository and protocol reading come before runtime testing. The
actual sequence was the reverse: the hardest variants of runtime testing were
performed first, and the ungated repository-reading steps were deferred.

**A self-invented goal replaced the spec's goal.** The working objective became
"prove the native dependency closure runs", which was constructed during the work
rather than derived from the spec's definition of done. Optimising against a
self-authored metric is what allowed twenty milestones to be spent without any of
them testing the assumption the whole effort rested on.

**Requirements were inferred rather than read.** One concrete instance: `orjson`
survived in the native-dependency list across several milestones and was built
against as a real requirement. A later audit of the actual Hermes source showed
`orjson` is not in the dependency closure at all - the real Rust/PyO3
dependency is `jiter`, reached through `openai==2.24.0`. That correction is
recorded in `docs/M0-008N_DEPENDENCY_AUDIT_CORRECTION.md`.

The pattern, not the single instance, is the lesson: a dependency inferred from
memory rather than read from an artifact is a guess, and a guess can survive many
milestones while looking confirmed because each milestone builds on the previous
one rather than re-reading the source.

---

## Valuable technical output

**This was not wasted work, and the embedded path did not fail.**

The five packages above are genuine, reusable infrastructure, proven with a
build-to-device SHA identity chain. The technical feasibility of running Hermes
natively on Android arm64 is **established**, not disproven. Any account of this
period that says the embedded approach "failed", "did not work", or "was proven
impossible" is false and is contradicted by the device evidence in the milestone
documents.

What changed is not that the embedded path failed. It is that the embedded path
is **no longer the default**, for reasons recorded in `PIVOT-DECISION.md` rather
than here.

---

## What changed

The default architecture is now **remote Hermes with a thin Android client**. The
previous embedded / Chaquopy / native-dependency work is preserved as **Version-B
/ Full Offline Mode** research.

It is parked, not discarded. See OD-004 in `docs/OPEN-DECISIONS.md` for the
conditions that would reactivate it, and the Requirement-Change Rule in
`PIVOT-DECISION.md` for what counts as a reason to reopen the decision.

---

## Process controls going forward

1. **Follow the spec's gates in order.** A gate that can be executed without a
   device should be executed before one that cannot.

2. **Decide before implementing.** Product questions - does this need to be
   offline, does this need to be local - are decisions, and they precede
   engineering work rather than following it.

3. **Consult before an architecture pivot, not after.** An independent review
   during the M0 series would have surfaced the cost of the CPython-for-Android
   toolchain as a recurring maintenance obligation rather than as per-milestone
   discovery.

4. **Read requirements from artifacts.** Dependencies, versions and constraints
   come from package metadata and upstream source, never from recollection.

5. **Historical audit trail is not rewritten.** Corrections are appended and the
   superseded record is preserved. This document follows the same rule: it
   explains the approach taken, and it does not revise the milestone records.