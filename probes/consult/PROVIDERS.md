# Consultation providers — measured behaviour

## Correction, 2026-10-04

An earlier version of this file said the Claude endpoint "discards history" and
implied the model has no memory. **Both parts were wrong as stated.**

Claude *does* have full within-session memory — demonstrated directly: given a
transcript, it answers from it correctly. What the endpoint at
`127.0.0.1:8787` does is **reject multi-message payloads** (502 Bad Gateway,
5 of 5 attempts). A single user message containing the whole transcript works
and the model reasons over it correctly.

So this is a transport limitation, not a capability limitation. It is also a
cheap one to work around.

## What each provider accepts

| | Qwen | Claude |
|---|---|---|
| Multi-message history | **honours it** | **rejected — 502** |
| Single message with transcript inside | works | **works, and the model uses it** |
| Model memory within a session | full | full |

## How `ask.ps1` handles this

For **claude**, prior turns are concatenated into ONE user message as a labelled
transcript rather than sent as a message array. This is what makes Claude usable
as a session participant rather than only as a one-shot reviewer.

For **qwen**, the real message array is sent, because that endpoint honours it.

Either way the session file under `sessions/` is the source of continuity.

## Claude's context ceiling — real, unlike the 400s

A size sweep showed the endpoint rejecting payloads from about 7 KB upward
(502), while 5.2 KB succeeded. That ceiling is genuine and separate from the
flakiness below. Keep Claude sessions under roughly 4 KB of replayed content.

## The 400s are transient and must not be believed

A size sweep gave a physically impossible curve: 8 KB OK, 16/24/32 KB FAIL,
**48 KB OK**. Repeating a 60-byte payload six times gave one timeout and five
successes.

So a single 400 is **not** evidence of a context limit. `ask.ps1` retries up to
four times with backoff before believing a failure.

## Working method

**Either provider can now hold a session.** Use one named session per workstream
(`m21`, `m21-arch`) and ask follow-ups there; the model remembers.

Claude remains the one to use for the final independent "did we get this right"
pass, because its independence is the point and forgetting the earlier draft is
harmless there.

**If a session starts failing repeatedly**, delete `sessions/<name>.json` and
re-seed. `CURRENT_STATE.md` is what makes a session worth resuming; without it
you pay full context cost per turn for nothing.