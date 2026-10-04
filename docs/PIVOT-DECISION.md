# Pivot Decision

Status: **FROZEN**
Decided by: project owner
Date: 2026-10-03
Supersedes: nothing. Amends the runtime assumptions of `HERMES_ANDROID_HARNESS_MASTER_SPEC_v3.md`.

---

## Product Requirement Decision

There is currently **NO hard product requirement** for:

- fully offline operation
- data residency

**Remote Hermes is therefore an allowed and preferred architecture.**

Recorded verbatim:

> "NO hard offline/data-residency requirement exists today."

---

## Architecture Decision

Default architecture: **Remote Hermes + thin Android client**.

```text
[Remote Host]
  Hermes Gateway / TUI Gateway
  Workspace: /workspace/<user>/<repo>
  WebSocket server: wss://
          |
          |  TLS
          v
[Android]
  thin Kotlin client
  protocol rendering
```

The Android client does NOT:

- host the Hermes brain
- implement `AIAgent`
- implement a second agent loop
- replace the Hermes gateway
- own the working tree

**The Hermes gateway remains the authoritative agent runtime.**

The gateway binary is unchanged **as of this decision**; the authentication and
session-security changes required by OD-001 are anticipated and unscoped, and may
modify it. The process being redeployed to the remote host is the same process
that was being verified for Termux or embedded deployment.

This is a redeployment, not a rewrite. That is a statement of **intent** - it
describes what was chosen - and it is deliberately **not** a guarantee that the
binary is frozen. Any implementation of OD-001 that adds authentication to the
gateway changes it.

---

## Workspace Ownership Decision

**CHOSEN: persistent workspace per user.**

An important clarification that this decision does *not* imply:

**Persistent workspace does not mean keeping a compute container running
continuously.**

Preferred model:

```text
User
  |
  v
Android Client
  |
  | WSS
  v
Remote Session Runtime        <- disposable
  |
  v
Persistent User Workspace    <- durable
  +-- .git
  +-- source code
  +-- generated files
  +-- checkpoints / artifacts
  +-- session-associated state
```

Principles:

- persistent storage owns the user working tree and Git state
- runtime / container may be ephemeral
- a new runtime can attach to an existing persistent workspace
- runtime failure must not destroy the workspace
- session resume must resolve back to the persistent workspace

Session lifecycle:

```text
session ends
    |
    v
runtime dies
    |
    v
workspace SURVIVES
    |
    v
new session
    |
    v
re-attach to same workspace
    |
    v
resume
```

This avoids turning remote infrastructure into a 24/7 server whose only purpose
is to hold a directory, without turning every session into an amnesiac island.

---

## Source-of-Truth Rule

**The remote workspace is authoritative** for the active remote architecture.

Android renders:

- file content
- diffs
- status
- agent activity
- artifacts

**Android does not require a local working tree.**

Consequence, stated explicitly because it is the crux of the pivot: a remote
Hermes cannot write to the phone's app-private storage, and it does not try to.
The repository is not local to the phone. The phone is a control surface for a
repository that lives on the remote host.

---

## Version-B Decision

The previous embedded / Chaquopy / native dependency work is **preserved** as
**Version-B / Full Offline Mode** research.

It is **NOT** the default architecture.

Do not delete it.

Do not continue native dependency closure unless a Version-B trigger is
activated. See `docs/OPEN-DECISIONS.md` OD-004.

---

## Explicit Frozen Decision

**Do NOT reopen the "offline vs remote" decision in subsequent sessions** unless
a new product requirement or an explicit owner decision changes it.

Recorded:

> "NO hard offline/data-residency requirement exists today."

---

## Local Cache Policy

**Status: OPEN - not decided.**

The pivot states that Android does not require a local working tree. That is a
statement about **authority**, not about **caching**, and the distinction matters
because it admits two very different implementations:

| Question | Status |
|---|---|
| May Android cache file content locally so the user can read it without a network round-trip? | **OPEN** |
| If it may, is that cache a working tree? | **OPEN** |
| May a cached copy be edited and synced back to the remote? | **OPEN** |
| May the cache ever hold authoritative state? | **OPEN - derived, not confirmed. See below.** |

### The one derived statement

> "The local cache may never hold authoritative state."

**This is DERIVED, not independently confirmed, and the derivation is shown here
so it can be checked rather than trusted.**

The governing text, quoted verbatim from the Source-of-Truth Rule above:

> "**The remote workspace is authoritative** for the active remote architecture."
> "...Android does not require a local working tree."

Derivation: a cache that held authoritative state would itself become a working
tree, contradicting "Android does not require a local working tree"; and it would
give the phone authority that the rule assigns to the remote workspace.

**The interpretive gap, stated plainly:** the Source-of-Truth Rule was written
about the *working tree*, and it does not mention caches explicitly. Reading
"a cache must not become authoritative" out of it requires one step of
generalisation. That step is small, and the resulting statement is hard to
disagree with, but it is still an interpretation.

If the owner rejects it, the correct home is a new open decision rather than a
sentence in a frozen document. It is recorded here as derived rather than decided.

Everything else in the table above is **OPEN** and belongs to the owner. It is not
decided here because each option produces a materially different client: read-only
caching, caching with write-back, and no caching at all are three different
products, and choosing between them is a product decision rather than a
documentation detail.

---

## Persistent Workspace Durability

**Status: OPEN - guarantees are not defined.**

The workspace is described as persistent. That word describes **intent**, not a
durability contract, and the distinction is recorded because it is load-bearing:
the remote workspace is now the only copy of the user's working tree and Git
state.

| Question | Status |
|---|---|
| Durability guarantee (what is the tolerated data-loss window?) | **OPEN** |
| Backup / snapshot policy | **OPEN** |
| Retention: how long after session end before a workspace is reclaimed? | **OPEN** |
| Disaster recovery: what happens when the volume is lost? | **OPEN** |

**Persistent does not mean undestroyable.** No claim of "cannot lose data" is made
here. Until the rows above are answered, the honest statement is that the remote
workspace is *intended* to persist and that its durability properties are
undefined. Treat "persistent" as a design direction, not as a promise.

---

## Multi-Device

**Status: OUT OF SCOPE for this decision - not ruled out.**

Whether one user operating the same workspace from two devices simultaneously is
a supported use case is **not decided**. Multi-device is not ruled out and not
adopted.

It is explicitly **dependent on OD-005**. "Two devices attached to one workspace"
is a workspace-concurrency problem, and the concurrency policy has not been chosen.
Claiming multi-device support before OD-005 is answered would be claiming a
capability whose semantics are undefined.

---

## Requirement-Change Rule

**Status: FROZEN - this defines what may reopen the decision above.**

The offline-vs-remote decision is frozen. "Frozen" is only enforceable if "new
requirement" is defined, because otherwise any agent may assert one.

Version-B (embedded / full-offline) may be reopened **only** by one of:

1. an explicit product requirement from the owner
2. a customer or contract requirement for offline operation
3. a regulatory, app-store or export-control constraint
4. a formally approved architecture or business decision, made by the owner

A developer, agent or model **may not** reopen it on the basis of:

- Android embedded execution being difficult or slow
- speculation about remote hosting cost
- speculation about latency at scale
- personal technical preference
- a preference for local execution over network execution

The reasoning is deliberately asymmetric. Items 1-4 are evidence that the world
changed. The excluded items are opinions about how the changed world might be
served, and they do not constitute a requirement. Accepting them would make the
freeze reversible by anyone with a strong opinion, which would render it
meaningless.

**This rule is itself subject to the owner's authority.** The owner may reopen the
decision directly. The constraint exists to stop *unauthorised* reopening, not to
bind the owner.

---

## Provenance of this decision

This decision was reached after an independent technical consultation with a
separate model (`claude-sonnet-5-high`) which was given the full master spec and
the actual M0 status, and which identified that the project had been solving the
packaging problem rather than the gating problem. The owner then answered the one
question that determined the direction: whether a hard offline or data-residency
requirement exists. The answer was no.

The prior work is **not wasted**. Five Hermes dependencies were proven to build
and execute on the physical Android device with full artifact identity
(`build SHA == APK SHA == device SHA`):

- `pydantic-core 2.46.4`
- `cffi 2.1.1`
- `cryptography 50.0.1`
- `httptools 0.8.0`
- `jiter 0.17.0`

That is genuine, reusable, hard-won infrastructure. It is parked, not discarded.

---

## Provenance note

How this decision was reached, and how the earlier work ended up off the critical
path, is recorded separately in `docs/RETROSPECTIVE.md`. That document explains
the approach only; it does not modify this decision.
