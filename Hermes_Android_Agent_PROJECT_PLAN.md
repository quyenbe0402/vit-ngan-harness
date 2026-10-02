# Hermes Android Agent — Project Execution Plan

> **Status:** Planning Baseline  
> **Date:** 2026-08-24  
> **Project:** Hermes Android Agent  
> **Primary platform:** Android  
> **Consumer build:** Version A  
> **Advanced build:** Version B Pro  
> **Development OS:** Windows 11 Home  
> **Primary IDE:** Android Studio  
> **Android language:** Kotlin  
> **UI:** Jetpack Compose  
> **Agent core reference:** `NousResearch/hermes-agent` v0.20.5  
> **Current Hermes upstream commit:** `13f4cfebfa`

---

# 1. Project Vision

Hermes Android Agent is an Android-native AI agent designed to combine:

- Hermes Agent capabilities
- Android-native tools
- structured device control
- memory
- skills
- tool calling
- deterministic automation
- optional advanced UI automation
- optional device-management privileges
- optional vision capabilities

The project is intentionally designed as a **layered agent runtime**, not as a chatbot with a few Android commands.

The central idea is:

```text
Hermes Brain
    ↓
Tool / Capability Router
    ↓
Android Command Engine
    ↓
UI Automation (optional)
    ↓
Privilege Layer (optional)
    ↓
Android OS
```

MediaProjection/Vision is intentionally outside the core execution path.

---

# 2. Core Product Concept

The product is split into two editions.

## Version A — Consumer / Play Store

Goal:

- normal Android installation
- minimal permissions
- no mandatory Device Owner
- no assumption of root
- no dependency on privileged system APIs
- safe Android-native tools
- Hermes agent capabilities
- memory and skills
- deterministic automation where applicable

Version A must not use Accessibility as an unrestricted LLM-controlled UI automation engine.

For the Play Store-oriented build:

```text
LLM
  ↓
Tool
  ↓
Android API / safe capability
```

Accessibility, if included, is limited to deterministic, human-defined automation flows and must remain consistent with applicable Google Play policy.

## Version B Pro — Advanced Agent

Goal:

- deeper Android automation
- advanced tools
- Accessibility-based LLM-driven UI automation where deployment/policy permits
- Device Owner / managed-device capability where provisioning supports it
- advanced execution backends
- enterprise/power-user capabilities
- optional privileged backends

Version B is an advanced deployment and is not assumed to have the same distribution constraints as Version A.

---

# 3. Non-Goals

The project will not initially attempt to:

- replace Android as an operating system
- become a root exploit
- bypass Android security controls
- force Device Owner onto already-managed consumer devices
- treat Accessibility as equivalent to root
- make MediaProjection mandatory
- rewrite all Hermes Python code into Kotlin
- modify the upstream Hermes repository directly

---

# 4. Architecture Overview

```text
                         USER
                           │
                           ▼
                  ┌─────────────────┐
                  │ Android UI      │
                  │ Jetpack Compose │
                  └────────┬────────┘
                           │
                           ▼
                ┌──────────────────────┐
                │ Hermes Agent Layer   │
                │                      │
                │ Agent Loop           │
                │ Reasoning            │
                │ Planning             │
                │ Memory               │
                │ Skills               │
                │ Tool Calling         │
                │ Sessions             │
                └──────────┬───────────┘
                           │
                           ▼
                ┌──────────────────────┐
                │ Capability / Tool    │
                │ Router               │
                └──────────┬───────────┘
                           │
            ┌──────────────┼──────────────┐
            ▼              ▼              ▼
      Android APIs       Shell       UI Automation
            │              │              │
            │              │        Accessibility
            │              │        UI hierarchy
            │              │        Gestures
            └──────────────┼──────────────┘
                           ▼
                ┌──────────────────────┐
                │ Privilege Layer      │
                │                      │
                │ Device Owner         │
                │ Privileged bridge    │
                │ OEM/system backend   │
                │ Root (optional)      │
                └──────────┬───────────┘
                           │
                           ▼
                      ANDROID OS

               Optional Vision Branch
               MediaProjection
                      ↓
                 Screen Capture
                      ↓
                  Vision Model
```

---

# 5. Layer Definitions

## Layer 1 — Hermes Brain

Responsibilities:

- agent loop
- reasoning
- planning
- model integration
- tool calling
- memory access
- skill loading
- session handling
- context handling
- task execution

The Hermes upstream project is treated as an upstream dependency/reference rather than a file tree to freely modify.

### Rule

The Brain must not directly access Android framework APIs.

It communicates through contracts/interfaces.

```text
Brain
  ↓
Tool Call
  ↓
Runtime
```

Not:

```text
Brain
  ↓
android.* API
```

---

## Layer 2 — Android Command Engine

Responsibilities:

- Android APIs
- PackageManager
- Activity/Intent operations
- Device state
- battery information
- network state
- app discovery
- safe file operations
- command execution backends
- system services
- device information

The Command Engine determines the safest available execution mechanism.

Preference order:

1. dedicated Android API
2. purpose-built Android service
3. safe command backend
4. privileged backend
5. root/OEM backend only when explicitly available

Do not use shell when a stable Android API is better.

---

## Layer 3 — UI Automation

Responsibilities:

- AccessibilityService integration
- UI hierarchy
- node discovery
- semantic actions
- gestures
- text input
- global actions

This layer is optional and capability-gated.

Version A:

- deterministic/human-defined automation only where appropriate

Version B:

- advanced LLM-driven UI automation may be enabled where deployment and policy permit

Important:

```text
Accessibility != root
Accessibility != Device Owner
Accessibility != unrestricted system control
```

---

## Layer 4 — Privilege Layer

Responsibilities:

- Device Owner integration
- DevicePolicyManager
- privileged bridge abstractions
- OEM/system backends
- optional root backend

Privilege is represented as capability providers, not hard-coded assumptions.

The application must never assume:

```text
Device Owner available
Root available
Privileged bridge available
```

Capabilities are discovered at runtime.

---

# 6. Optional Vision Layer

MediaProjection is not part of the core agent architecture.

It is an optional extension:

```text
MediaProjection
    ↓
Screen Capture
    ↓
Vision Model
    ↓
Structured Observation
    ↓
Hermes
```

The project should operate normally without it.

The current emulator has a known `screencap`/gfxstream limitation, therefore development and validation should prefer:

```text
uiautomator dump
dumpsys activity
dumpsys SurfaceFlinger
```

over `adb exec-out screencap`.

---

# 7. Shared Core / A-B Architecture

The two editions must share the same conceptual core.

```text
                    SHARED CORE
                         │
              ┌──────────┴──────────┐
              ▼                     ▼
         VERSION A              VERSION B
         Consumer               Advanced
              │                     │
         Safe Runtime         Extended Runtime
```

Shared components:

- agent interfaces
- tool schemas
- memory
- skills
- model adapters
- sessions
- capability contracts
- bridge protocol
- common data models

Edition-specific code belongs in runtime/capability modules.

---

# 8. Capability System

Every tool must declare its capability requirements.

Example:

```text
Tool: get_battery
Capability: DEVICE_BATTERY_READ

Tool: launch_app
Capability: APP_LAUNCH

Tool: uninstall_app
Capability: APP_MANAGEMENT

Tool: ui_click
Capability: UI_AUTOMATION

Tool: managed_device_policy
Capability: DEVICE_OWNER
```

Runtime flow:

```text
Hermes
  ↓
Tool Call
  ↓
Capability Resolver
  ↓
Allowed / Denied / Unavailable
  ↓
Execution
  ↓
Result
```

Critical security rule:

> The LLM is never the authority that grants itself a capability.

---

# 9. Tool System

Tool architecture:

```text
Tool
├── name
├── description
├── input schema
├── required capabilities
├── execution backend
├── result schema
└── error model
```

Initial tool groups:

## Device

- get_device_info
- get_battery
- get_storage
- get_network_state
- get_os_version

## Apps

- list_apps
- get_app_info
- launch_app
- stop_app
- open_app_settings

## Files

- list_files
- read_file
- write_file
- search_files

## Network

- connectivity_check
- network_info

## UI

- find_node
- click_node
- input_text
- swipe
- global_action

## System

- open_settings
- query_system_state
- inspect_process_state

Advanced tools will be added only after capability boundaries are defined.

---

# 10. Skills System

Skills are higher-level workflows built from tools.

Example:

```text
Skill: browser_search

Tools:
- launch_app
- open_url
- find_node
- input_text
- click_node
```

Skill responsibilities:

- define workflow
- define required tools
- define preconditions
- define expected results
- define error handling
- record reusable knowledge where appropriate

Skills must not bypass capability checks.

---

# 11. Memory System

Memory categories:

```text
Memory
├── Session memory
├── Conversation memory
├── Task memory
├── Device knowledge
├── Skill knowledge
├── User preferences
└── Long-term memory
```

The memory layer must be independent from UI and Android execution.

---

# 12. Hermes / Android Bridge

The bridge is a central project component.

Purpose:

- connect Hermes Python runtime and Android runtime
- transport tool calls
- transport events
- transport results
- propagate errors
- support cancellation
- support session state

Conceptual protocol:

```text
Hermes
  ↓
Request
{
  tool,
  arguments,
  session,
  request_id
}
  ↓
Android Runtime
  ↓
Capability Check
  ↓
Execution
  ↓
Response
{
  request_id,
  status,
  result,
  error
}
```

Protocol design must remain deterministic and versioned.

---

# 13. Physical Module Boundaries

Gradle modules should reflect architecture.

Proposed structure:

```text
:app

:core-brain
:core-agent
:command-engine
:ui-automation
:privilege-manager
:data-memory
:skills
:bridge
```

Dependency direction must remain intentional.

Example:

```text
app
 ├── core-agent
 ├── command-engine
 ├── ui-automation
 ├── privilege-manager
 ├── data-memory
 └── bridge

core-agent
 ├── core-brain
 ├── skills
 └── data-memory

command-engine
 └── Android APIs

ui-automation
 └── Accessibility APIs

privilege-manager
 └── device/system privilege integrations
```

Critical rule:

> `core-brain` must not depend on Android UI, Android framework, Device Owner, or Accessibility.

---

# 14. Repository Structure

```text
workspace/
│
├── hermes-agent/
│   └── Upstream Hermes
│
├── android/
│   └── HermesAndroid/
│
├── bridge/
│
├── tools/
│
├── skills/
│
├── docs/
│
├── scripts/
│
└── tests/
```

Android project:

```text
HermesAndroid/
└── app/
    └── src/main/
        ├── java/com/hermes/android/
        │   ├── ui/
        │   ├── agent/
        │   ├── bridge/
        │   ├── tools/
        │   ├── device/
        │   ├── automation/
        │   ├── privilege/
        │   ├── memory/
        │   ├── skills/
        │   └── data/
        └── res/
```

---

# 15. Current Environment Baseline

## Host

- Windows 11 Home build 26200
- native Windows workflow
- WSL2 not required
- PowerShell 5.1
- Git Bash

## Android

- Android Studio with bundled JBR 25.0.2
- Android SDK API 37
- Android platform 36.1
- platform-tools 37.0.1
- build-tools 37.0.0
- Emulator 37.1.11.0
- AVD: `HermesTest`
- Pixel 6 profile
- API 36.1 Google APIs x86_64

## Kotlin / Gradle

- AGP 9.3.0
- Gradle 9.5.0
- Kotlin 2.2.10
- Jetpack Compose BOM 2026.08.00
- Compose 1.12.0
- compileSdk 37
- targetSdk 37
- minSdk 26

## Hermes

- Hermes Agent v0.20.5
- upstream commit `13f4cfebfa`
- Python 3.13.15
- uv 0.11.31
- Node.js 24.18.0
- Git 2.55.0.windows.3
- Git LFS 3.7.1

## Existing Android test project

```text
workspace/android/HermesAndroid
package: com.hermes.android
```

Current build:

- successful
- debug APK generated
- APK install tested
- app launch tested
- UI verified through UIAutomator dump

---

# 16. Emulator Known Limitation

The current emulator version has a gfxstream/gralloc readback problem affecting:

```text
adb exec-out screencap
```

and related readback paths.

Applied mitigation:

```bash
adb root
adb shell setprop debug.sf.luma_sampling 0
```

Persistence:

```text
/data/local.prop
```

Validation must prefer:

```bash
adb shell uiautomator dump /sdcard/window_dump.xml
adb shell cat /sdcard/window_dump.xml
adb shell dumpsys activity activities
adb shell dumpsys SurfaceFlinger
```

Do not make screenshot capture a dependency of core development.

---

# 17. Development Strategy

The project should be developed in phases.

## Phase 0 — Governance

Create:

- `CLAUDE.md`
- `ARCHITECTURE.md`
- `PROJECT_PLAN.md`
- ADR directory
- module boundaries
- Git workflow
- test strategy

Deliverable:

```text
Architecture baseline locked
```

---

## Phase 1 — Android Application Foundation

Build:

- Compose navigation
- Home screen
- Chat screen
- Settings
- Tool screen
- session state
- error handling
- basic local storage

Deliverable:

```text
Android shell is stable
```

---

## Phase 2 — Hermes Bridge

Build:

- process/runtime strategy
- request/response protocol
- request IDs
- cancellation
- errors
- session association
- logging

Deliverable:

```text
Kotlin ↔ Hermes communication works
```

---

## Phase 3 — Tool Registry

Build:

- Tool interface
- Tool schema
- Tool registry
- Tool router
- capability declarations
- result model

Initial tools:

- device info
- battery
- app list
- launch app
- settings navigation

Deliverable:

```text
Hermes can call Android tools
```

---

## Phase 4 — Version A

Build:

- safe Android APIs
- minimal permissions
- deterministic automation where appropriate
- consumer UX
- permission explanation
- capability reporting
- Play Store-oriented packaging

Deliverable:

```text
Consumer Edition usable without Device Owner
```

---

## Phase 5 — Memory + Skills

Build:

- session memory
- persistent memory
- skill manager
- skill loading
- skill validation
- tool composition

Deliverable:

```text
Agent can retain and reuse structured knowledge
```

---

## Phase 6 — Advanced Automation

Build:

- AccessibilityService integration
- UI hierarchy parser
- semantic node lookup
- gestures
- text input
- automation execution

This is an advanced capability.

Deliverable:

```text
Version B can perform advanced UI automation
```

---

## Phase 7 — Privilege Manager

Build:

- capability detection
- Device Owner integration
- DevicePolicyManager adapter
- privileged backend abstraction
- optional bridge backend
- optional root/OEM backend

Deliverable:

```text
Privilege is modular and capability-driven
```

---

## Phase 8 — Version B Pro

Build:

- advanced tools
- advanced automation
- managed-device mode
- enterprise/power-user features
- advanced execution backends

Deliverable:

```text
Advanced Agent Edition
```

---

## Phase 9 — Optional Vision

Only after code/UI automation is stable.

Build:

- MediaProjection
- screen capture pipeline
- vision adapter
- structured visual observations
- optional screenshot reasoning

Deliverable:

```text
Vision-enhanced agent
```

---

# 18. AI Coding Governance

AI agents are allowed to modify code only within declared scope.

Each task should state:

1. target module
2. target files
3. expected behavior
4. constraints
5. tests required

AI must report:

```text
Files changed
Behavior changed
Tests executed
Tests passed
Known risks
```

Never accept:

```text
"Fixed. Everything works."
```

without evidence.

---

# 19. Protected Areas

Human review is required before merging substantial changes to:

```text
privilege-manager/
capability-resolver/
tool-router/
bridge/
accessibility/
Device Owner integration
permission declarations
Play Store compliance-sensitive code
```

AI may propose changes.

AI should not silently expand permissions or privilege surfaces.

---

# 20. Project Memory Files

Required files:

```text
CLAUDE.md
ARCHITECTURE.md
PROJECT_PLAN.md

docs/
├── boundaries.md
├── capabilities.md
├── testing.md
├── release.md
└── decisions/
    ├── ADR-001-hermes-core.md
    ├── ADR-002-a-b-editions.md
    ├── ADR-003-accessibility-policy.md
    └── ADR-004-privilege-layer.md
```

Purpose:

- preserve decisions
- prevent architecture drift
- reduce repeated explanation
- give AI coding agents stable context
- record why a design exists

---

# 21. Code Intelligence Layer

Evaluate code-intelligence tools rather than trusting README claims.

## First candidate

`codegraph-ai/CodeGraph`

Test on a small Kotlin sample before adopting.

Test queries:

```text
Who calls ToolRouter?
What depends on CapabilityResolver?
What breaks if ToolResult changes?
Show all callers of CommandEngine.execute()
Find tests related to LaunchAppTool
```

## Fallback

Sourcegraph precise code navigation / SCIP-based indexing.

The Kotlin implementation should be validated against the current toolchain and Kotlin version rather than assuming parser compatibility.

Code intelligence is an aid, not the source of truth.

Source of truth hierarchy:

```text
1. Source code
2. Tests
3. Architecture decisions
4. Build constraints
5. Code intelligence index
```

---

# 22. Testing Strategy

## Unit tests

Test:

- Tool schemas
- Tool routing
- capability resolution
- memory
- skill parsing
- model adapters
- bridge messages

## Integration tests

Test:

```text
Agent
 ↓
ToolRouter
 ↓
CommandEngine
 ↓
Fake Android backend
```

## Device tests

Run against `HermesTest` AVD:

- install
- launch
- app discovery
- launch app
- battery query
- settings navigation
- UIAutomator verification
- permission/capability states

## Regression principle

Every bug fix should add a regression test whenever practical.

---

# 23. Git Strategy

Rules:

- do not work directly on `main`
- one task per branch
- keep commits small
- review `git diff`
- merge only after tests pass

Example:

```bash
git checkout -b feature/tool-registry
git diff
git status
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Commit messages should describe intent, not implementation trivia.

---

# 24. CI

CI should eventually run:

```text
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Optional later stages:

- instrumentation tests
- AVD-based tests
- security checks
- dependency checks
- static analysis
- release build

CI goal:

> A change should fail automatically when it violates existing behavior.

---

# 25. Security Principles

1. Least privilege.
2. Capability-gated tools.
3. LLM cannot grant itself permissions.
4. No hidden privilege escalation.
5. No silent permission expansion.
6. Separate safe and privileged execution.
7. Log sensitive tool executions.
8. Do not place API keys in source control.
9. Keep upstream Hermes changes isolated.
10. Human review for security-sensitive modules.

---

# 26. Release Strategy

## A Release

Focus:

- usability
- stability
- minimal permissions
- consumer UX
- clear disclosures
- Play Store compliance
- strong testing

## B Pro Release

Focus:

- advanced automation
- device administration
- advanced capabilities
- controlled deployment
- explicit permission/provisioning flow

Do not treat Pro as a hidden policy bypass.

---

# 27. Current Project State

```text
Environment                    ✅
Android build                  ✅
Compose UI                     ✅
Hermes upstream                ✅
Python runtime                 ✅
ADB                            ✅
AVD                            ✅
UIAutomator verification       ✅

Bridge                         ⏳
Tool Registry                  ⏳
Capability Manager             ⏳
Command Engine                 ⏳
Memory                         ⏳
Skills                         ⏳
Version A                      ⏳
Accessibility                  ⏳
Privilege Manager              ⏳
Version B Pro                  ⏳
Vision                         ⏳
```

---

# 28. Immediate Next Steps

The first implementation sprint should **not** start with Accessibility, Device Owner, root, or Vision.

The correct order is:

```text
1. Lock architecture documents
2. Create Gradle module boundaries
3. Define common data models
4. Define Tool interface
5. Define Capability interface
6. Define Bridge protocol
7. Implement one end-to-end tool
8. Add tests
9. Validate on HermesTest AVD
10. Expand tool catalog
```

Recommended first vertical slice:

```text
User
 ↓
Compose Chat
 ↓
Hermes
 ↓
Tool Call: get_battery
 ↓
Capability Resolver
 ↓
Android Battery API
 ↓
Tool Result
 ↓
Hermes
 ↓
UI
```

Do not add a second major subsystem until this path is stable.

---

# 29. Definition of Done

A phase is complete only when:

- code exists
- module boundary is correct
- tests exist
- tests pass
- behavior is verified on device when relevant
- architecture docs are updated
- no unauthorized dependency direction was introduced
- Git diff has been reviewed

---

# 30. Final Architecture Principle

The project is built around one fundamental rule:

> **Hermes decides what it wants to accomplish. The Android runtime decides how it can be accomplished. The Capability System decides whether it is allowed.**

Therefore:

```text
Hermes Brain
    ≠
Android OS authority
```

and:

```text
Permission
    ≠
Capability
    ≠
Tool
    ≠
Skill
```

They are separate concepts and should remain separate in the implementation.

---

# 31. Long-Term Target

The long-term system should be able to evolve into:

```text
                    HERMES ANDROID
                         │
               ┌─────────┴─────────┐
               ▼                   ▼
          Consumer A           Pro B
               │                   │
               └─────────┬─────────┘
                         ▼
                   Shared Agent Core
                         │
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
        Tools          Skills         Memory
          │              │              │
          └──────────────┼──────────────┘
                         ▼
                Capability Runtime
                         │
       ┌─────────────────┼─────────────────┐
       ▼                 ▼                 ▼
 Android APIs       UI Automation      Privilege
       │                 │                 │
       └─────────────────┼─────────────────┘
                         ▼
                    Android OS
                         │
                 Optional Vision
```

This document is the planning baseline. Implementation decisions that materially change the architecture should be recorded as ADRs rather than silently changing the system.
