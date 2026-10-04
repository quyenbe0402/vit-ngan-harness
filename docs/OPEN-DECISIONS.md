# Open Decisions

Purpose: record decisions that are **still open**. This document does not decide
anything. Each entry states the question, what must be considered, and that the
answer has not been chosen.

These decisions are **owner-only**. They are not to be resolved implicitly by an
implementation agent.

Related: `docs/PIVOT-DECISION.md` (frozen decisions).

---

## OD-001 — WebSocket authentication / session model

**Status: OPEN**

**Question**

How should remote Hermes WebSocket sessions authenticate, resume, expire and
rotate credentials?

**Must be considered**

- TLS
- session authentication token
- token lifetime
- reconnect
- resume
- revocation
- device / session binding
- unauthorized connection handling

**Why it matters**

Under the previous on-device topology the trust boundary was "same device". Under
the remote topology the phone and the gateway are on different hosts, so the
previous assumption no longer holds. This decision is a security boundary
decision, not a convenience feature.

**Do not choose an implementation yet.**

---

## OD-002 — APK delivery / section 58

**Status: OPEN**

**Question**

How does the remote Hermes environment deliver an APK or other artifact to the
Android device?

**Architectural change that must be recorded**

A remote host cannot use `adb` against an arbitrary phone over the public
internet. The original section 58 success scenario assumed a USB / same-device
`adb` path. That assumption does not hold in the remote topology and the scenario
must be amended rather than merely relabelled.

**Options that may be considered**

- artifact transfer over an authenticated WebSocket
- HTTPS artifact endpoint
- Android-initiated download
- optional adb-over-LAN fallback
- other explicitly reviewed mechanism

**Do not choose an implementation yet.**

---

## OD-003 — Hosting provider / cost model

**Status: OPEN**

**Question**

Which hosting provider and runtime model is used for the remote Hermes host?

**Must be recorded**

- CPU / RAM expectations
- persistent storage
- concurrency
- estimated cost model
- cold-start implications
- network requirements

Interaction with OD-002 and with `PIVOT-DECISION.md`: the workspace is persistent
per user while the runtime may be ephemeral, so cold-start behaviour directly
affects user-visible latency.

**Do not invent pricing.** Pricing must be measured from the actual provider.

---

## OD-004 — Version-B activation trigger

**Status: OPEN**

**Question**

When should embedded / full-offline mode become active work again?

**Current decision**

**No trigger exists today.**

**Possible explicit triggers**, surfaced by consultation:

1. a customer or contract requires offline operation
2. an app-store or export-control constraint
3. remote hosting cost or latency becomes unacceptable at scale

**Recorded state**

Do not mark this as activated. Re-open only when one of the above actually
occurs, and only by explicit owner decision.

The Version-B asset that would be reactivated is preserved and documented in
`docs/SALVAGE-INVENTORY.md` when that inventory is written.
