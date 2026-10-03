# M0-008E Distribution / Signing Architecture Audit

> **SUPERSEDED by M0-008F (see `docs/M0-008F_TERMUX_PLUGIN_PROOF.md`).**
>
> The conclusion below that a matching Termux signer is *sufficient* to obtain
> shared-UID membership is **OBSOLETE**. M0-008F installed a real plugin with a
> bit-identical signer on the physical device and it was allocated its own appId
> (10384), not Termux's (10361). This document is retained as historical
> evidence. Where it says Model A is deployable, read M0-008G instead:
> stock-Termux + external plugin is **BLOCKED**.

## 1. Current blocker

M0-008D established the architecture is sound and the blocker is distribution.
This audit answers the technical half: **can this project deploy a Termux-side
bridge without an unsupported security bypass or an unverifiable signing
assumption?**

## 2. Android platform constraints

**VERIFIED FROM SOURCE.** `android:sharedUserId` requires every app in the group
to be signed with the **same certificate**. A mismatch is rejected at install
(`INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES`), so a shared UID cannot be
joined by an independently-signed app. A signature *permission* cannot
substitute: it grants an IPC authorisation, not filesystem or execution access
inside another app's sandbox.

## 3. Termux architecture

**VERIFIED FROM SOURCE / OBSERVED ON DEVICE.**

- Termux 0.118.3 declares `android:sharedUserId="com.termux"`.
- On the device: `sharedUser=SharedUserSetting{cd7ecc7 com.termux/10361}`.
- Termux' official plugins (`termux-boot`, `termux-api`, `termux-tasker`) declare
  the same shared UID, so the plugin model runs *inside* Termux' sandbox.

## 4. The decisive signing finding

Termux' repository publishes `app/dev_keystore.jks` **with its credentials in
plaintext** (`storePassword`/`keyPassword` in `app/build.gradle`), alias
`CN=APK Signer, OU=Earth, O=Earth`.

| Artefact | SHA-256 |
|---|---|
| `dev_keystore.jks` from termux-app @ v0.118.3 | `B6DA0148…8EE5E1` |
| Signer #1 of the **installed** Termux 0.118.3 | `b6da0148…8ee5e1` |

**They match exactly.** The GitHub-distributed Termux build is signed with the
publicly published development key.

Consequence: a plugin signed with that keystore can be installed into Termux'
shared UID on a device running that build. This is a verifiable fact, not an
assumption.

## 5. Model A — stock Termux + third-party plugin

**VIABLE, with a stated precondition.**

- Technical viability: yes — joins the `com.termux` shared UID, executes inside
  Termux' sandbox, leaves Termux stock and untouched.
- Signing: the published `dev_keystore.jks`, or an official Termux release key.
- Limitation: **only for Termux builds signed with that key** (the GitHub release
  line). F-Droid / Play builds use different signing and would **not** admit a
  plugin signed with the dev key.

### Security note, stated plainly

Because that keystore is public, **any** app signed with it could join Termux'
shared UID on such devices. Termux publishes it for reproducible builds. That is a
property of Termux' distribution choice, not something this project creates — but
it means a plugin signed with it is not by itself a strong security boundary.
The bridge must still enforce its own permission, caller identity and tokens.

## 6. Model B — custom Termux distribution

Technically viable: build from source with the bridge included. Costs: a
non-standard signing identity; cannot coexist with an installed stock Termux
(same package and shared UID, different signer); maintenance whenever upstream
Termux changes.

## 7. Model C — bundled Pro runtime

Technically the same mechanics as B, shipped as a paired APK set. Install-order,
coexistence and update issues are identical; the only difference is who
distributes it. No store-policy claim is made — that was not verified here.

## 8. Model D — upstream contribution

**TECHNICALLY POSSIBLE.** A generic, Hermes-agnostic process/stream service in
Termux would be a normal addition: exported, permission-guarded, returning
descriptors. Whether maintainers accept it is **UNKNOWN** and is not speculated
about. Hermes-specific logic stays outside Termux either way.

## 9. Signing analysis

| Question | Answer |
|---|---|
| Can an independent signer join `com.termux`? | No - the certificate must match |
| Is Termux's signing key public? | Yes, for the GitHub/dev line (`dev_keystore.jks`) |
| Is the installed Termux on this device using it? | **Yes** - digests match |
| Can a signature permission replace shared UID? | No - it grants IPC rights, not sandbox access |

## 10. Security analysis

All models share the same bridge requirements: exported service behind a custom
`dangerous` permission, framework-resolved caller UID re-checked, per-session
token, rejection **before** spawning, and **no** `execute(String)`. The bridge
exposes only structured operations: process lifecycle + stream transport +
authorization. No model-provider logic, no agent loop, no MCP, and no workspace
or capability policy inside the bridge.

## 11. Distribution table

| Model | Technical viability | Signing requirement | Stock Termux compatible | Maintenance | Install complexity | Hermes suitability |
|---|---|---|---|---|---|---|
| A | Viable | published dev key (or official) | Yes, for that key's builds | Low | Low (one extra APK) | Yes |
| B | Viable | own key | No (cannot coexist) | High | Medium | Yes |
| C | Viable | own key | No (cannot coexist) | High | High (pair install) | Yes |
| D | Technically possible | upstream decides | Yes, after merge | Shared with Termux | None after merge | Yes |

## 12. Hermes compatibility

Identical across models: the bridge carries three unidirectional FDs, which is
what `gateway.ready` plus newline-delimited JSON needs. Hermes runs unchanged
inside Termux - already proven in M0-007B.

## 13. Technical conclusion

**YES - DEPLOYABLE MODEL IDENTIFIED.**

Model A is deployable **without** a security bypass and **without** an
unverifiable signing assumption: the required key is published by Termux itself
and its digest has been matched against the installed build on this device.

## 14. What must happen before implementation

1. Build the plugin APK signed with `dev_keystore.jks`, declaring
   `sharedUserId="com.termux"`.
2. Verify the install succeeds and the plugin joins uid 10361. This is the check
   that proves the whole chain, and it has **not** been performed yet.
3. Only then implement `TermuxRuntimeBackend` against it.

## 15. Product decision still required

**Technical result:** Model A is deployable now, on Termux builds signed with the
published key.
**Product decision:** which Termux distribution to target, and whether to depend
on a public development keystore, remains with the project owner. No pricing,
edition or distribution-policy decision is made here.