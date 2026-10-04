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

The gateway binary does not change. Its address does. The same process that was
being verified for Termux or embedded deployment is the process that runs on the
remote host. This is a redeployment, not a rewrite.

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
