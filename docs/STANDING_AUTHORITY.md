# Standing Authority — delegated technical decisions

**Status: ACTIVE**
**Set by:** project owner
**Date:** 2026-10-04

## The rule

**Decisions returned by `claude-sonnet-5-high` are the owner's decisions.**

Take the returned result and **execute it automatically**. Do not stop to ask
the owner for approval on technical decisions: library selection, architecture,
code structure, naming, test design, which of two engineering arguments is
correct, or which rejection reason holds.

This supersedes the earlier practice of consulting the owner before each report
in those categories.

## Consultation gate — still mandatory

The gate itself is **not** relaxed. Every substantive decision still goes through
`probes/consult/ask.ps1 -Provider claude` before it is executed.

This is not a formality. Claude has no independent authority: it reaches the
repository through this agent, so a wrong Claude recommendation becomes a wrong
action unless it is checked. Under automatic execution the gate is the **only**
control that remains. Removing it would remove the check entirely.

Reported evidence labels are unchanged. A delegated decision is still only
`INFERRED` until a build or a device run proves it.

## Categories that still pause and ask

Narrow, and listed so the boundary is predictable rather than discretionary.

1. **Irreversible or destructive git operations** — force-push, history rewrite,
   rebase of shared history, deleting branches, pruning objects.
2. **Anything leaving the machine** — pushing, publishing, deploying to a real
   host, sending provider requests, spending money.
3. **Credentials and secrets** — creating, rotating, revoking, or writing a
   token anywhere.
4. **A product requirement, not a technical decision.** Claude may flag these as
   owner questions; that flag is honoured rather than silently decided. When
   forced to proceed, the assumption is recorded explicitly as *derived, not
   confirmed*, following the pattern already used for Local Cache Policy in
   `PIVOT-DECISION.md`.
5. **Deleting or rewriting Version-B / historical material.** Preserved unless
   the owner says otherwise.

## Working method

Parallel fan-out is encouraged: several provider calls at once, one scoped
question each. Qwen (`Qwen-3.8-Max`) is available as a delegated coding
sub-agent for implementation-shaped questions; Claude reviews the result before
it is acted on when the decision is architectural.

**Two lessons from this project are binding, not advisory:**

- Read requirements from artifacts, never from recollection. The `orjson`
  dependency survived several milestones because it was inferred from memory.
- A second implementation by the same author catches implementation-consistency
  bugs, **not** spec-comprehension bugs. Do not let it be written up as
  independent verification.

## Review trigger

This delegation may be withdrawn by the owner at any time. If a delegated
decision later proves wrong, the correction is appended to the relevant document
and the class of decision is referred back to the owner — history is not
rewritten.