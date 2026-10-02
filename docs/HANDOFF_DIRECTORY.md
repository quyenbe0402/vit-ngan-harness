# Handoff Directory

`handoff/` is the **transport area** between the two agents.

---

## What it is for

Claude and Cline run in different environments and often cannot talk to each
other directly. `handoff/` is where the artifacts that cross that boundary
are placed, so the next agent can find them without being told.

---

## What it is NOT

**`handoff/` is not the source of truth.**

The source of truth is GitHub: branch → commit → pull request → merged
history. A file sitting in `handoff/` describes work; a commit on GitHub
*is* work. If they disagree, GitHub is right and the file is stale.

Anything in `handoff/` that is not committed is a local note to yourself. It
does not travel to the other agent and it does not survive a fresh clone.

---

## Structure

```
handoff/
├── incoming/     <- artifacts Cline has received and is acting on
├── outgoing/     <- artifacts Claude has produced for Cline
├── reports/      <- Cline's integration reports, Logcat, device summaries
└── archive/      <- closed handoffs, kept for history
```

Each directory contains a `.gitkeep` so that Git tracks the empty folder.

---

## `incoming/` — received by Cline

Contains the handoff documents Cline is currently acting on.

```
handoff/incoming/M0-001-handoff.md
```

A file lands here when Cline fetches and reads it. It is a working copy of
something that already exists elsewhere. Move it to `archive/` when the task
is closed.

---

## `outgoing/` — produced by Claude

Contains the handoff documents Claude has produced.

```
handoff/outgoing/M0-001-handoff.md
```

Must contain every field in `docs/HANDOFF_PROTOCOL.md` §3.

---

## `reports/` — produced by Cline

The evidence directory. Everything here is produced by running something.

```
handoff/reports/
├── M0-001-integration.md                 <- the integration report
├── logcat-<serial>-<timestamp>.txt       <- Logcat captures
└── device-test-<timestamp>.txt           <- device test summaries
```

The `logcat-*.txt` and `device-test-*.txt` files are generated
automatically by `scripts/collect-logcat.ps1` and
`scripts/full-device-test.ps1` into this directory. They are gitignored
(`*.log`, `logcat-*.txt`) because raw device logs are large, machine
specific, and usually contain fragments of user data. The **summary** in
the integration report is what gets committed; the raw log stays local
unless it is needed as evidence, in which case commit it deliberately and
review it first for anything sensitive.

---

## `archive/` — closed handoffs

```
handoff/archive/M0-001/
├── handoff.md
└── integration.md
```

Both directions of a closed handoff are kept together, so the history of
what Claude built and what Cline found is visible together. Move a task's
artifacts here only after both sides are complete.

Archive is a convenience for humans reading the history. It is not part of
the authoritative state.

---

## What must NEVER go in `handoff/`

- API keys, tokens, PATs, passwords
- SSH private keys or key material
- Anything copied from a credential store
- Full dumps of a device's storage or user data
- A `local.properties` file

See `docs/SECRETS_POLICY.md`. This directory is committed, so a secret
placed here is a secret in Git history.

---

## Conventions

| Type | Location |
|------|----------|
| Handoff document | `<task-id>-handoff.md` |
| Integration report | `<task-id>-integration.md` |
| Logcat | `logcat-<serial>-<YYYYMMDD-HHMMSS>.txt` |
| Device summary | `device-test-<YYYYMMDD-HHMMSS>.txt` |

Task IDs follow `docs/HANDOFF_PROTOCOL.md` §7: `M<milestone>-<sequence>`.

Filenames are lowercase with hyphens. They are referenced by path in
reports, so a stable name matters more than a pretty one.
