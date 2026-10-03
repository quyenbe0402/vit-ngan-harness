package dev.vitngan.harness.core.runtime

/**
 * The M0-007 feasibility verdict, recorded as data.
 *
 * This is a *record of an audit*, not an implementation. No runtime here
 * launches Hermes, and nothing in this file makes the app appear to run it.
 * The evidence behind every field is in
 * `docs/M0-007_RUNTIME_FEASIBILITY_AUDIT.md`.
 */
enum class RuntimeCandidate {
    TERMUX,
    EMBEDDED_PYTHON,
}

/** Evidence-backed feasibility outcome for a candidate. */
enum class Feasibility {
    FEASIBLE,
    PARTIALLY_FEASIBLE,
    NOT_FEASIBLE,
}

/**
 * What the audit actually established, with the reason attached.
 *
 * Keeping the reason next to the verdict is deliberate: a bare "FEASIBLE" would
 * be unfalsifiable, and the next engineer would have to redo the analysis to
 * learn whether it still holds.
 */
data class FeasibilityFinding(
    val candidate: RuntimeCandidate,
    val verdict: Feasibility,
    val reason: String,
    val evidence: List<String> = emptyList(),
)

/**
 * Static, audited facts about the Hermes revision this project targets.
 *
 * These are upstream properties at commit `eaecc99c`, not device probes, and
 * they are the reason the embedded-Python candidate fails. They are recorded
 * here so a future change to any of them is a deliberate, reviewable edit.
 */
object HermesRuntimeRequirements {
    const val AUDITED_COMMIT = "eaecc99c"

    /** `requires-python = ">=3.11,<3.15"` and `.python-version` = 3.14. */
    const val PYTHON_FLOOR = ">=3.11"
    const val PYTHON_TARGET = "3.14"

    /** `.nvmrc` = 26. */
    const val NODE_TARGET = "26"

    /**
     * Core dependencies gated on `python_version >= '3.14'`.
     *
     * This is the decisive number: it is *every* core dependency. An interpreter
     * below 3.14 does not merely risk a bug - `openai`, `httpx`, `requests`,
     * `pydantic`, `rich`, `prompt_toolkit`, `websockets` and the rest are not
     */
    const val GATED_DEPS_ON_PYTHON_314 = 45

    /** Upstream ships `[termux]` and `[termux-all]` extras; uvloop is omitted. */
    const val HAS_TERMUX_EXTRAS = true
}

/**
 * The M0-007 conclusions.
 *
 * - Termux is FEASIBLE: it ships Python 3.14.6 aarch64 (satisfying the floor
 *   and every gated dep) and Node 26.4.0 aarch64 (matching `.nvmrc`), and
 *   upstream declares Android/Termux as a supported target.
 * - Embedded Python / Chaquopy is NOT FEASIBLE *for this revision*: no
 *   Android CPython reaches 3.14, and the required native packages publish
 *   zero Android wheels at the pinned versions.
 *
 * The embedded candidate is rejected, not deleted: if upstream ever relaxes
 * its Python floor, the `HermesRuntime` abstraction stays valid and the
 * decision can be revisited on evidence.
 */
object RuntimeFeasibilityAudit {

    val findings: List<FeasibilityFinding> = listOf(
        FeasibilityFinding(
            candidate = RuntimeCandidate.TERMUX,
            verdict = Feasibility.FEASIBLE,
            reason = "Termux provides Python 3.14.6 aarch64 and Node 26.4.0 aarch64, satisfying " +
                "the audited interpreter floor and all 45 python_version>=3.14 gated " +
                "dependencies; the native closure builds from sdist in Termux's userland, " +
                "and upstream ships [termux]/[termux-all] extras declaring Android supported.",
            evidence = listOf(
                "Termux termux-main pool: python_3.14.6-1_aarch64.deb",
                "Termux termux-main pool: nodejs_26.4.0-1_aarch64.deb",
                "pyproject.toml requires-python >=3.11,<3.15 with 45 core deps gated on 3.14",
                "upstream PR #100574 selects a supported Python on Termux",
            ),
        ),
        FeasibilityFinding(
            candidate = RuntimeCandidate.EMBEDDED_PYTHON,
            verdict = Feasibility.NOT_FEASIBLE,
            reason = "No Android CPython runtime reaches 3.14, so the 45 gated core " +
                "dependencies - including the LLM client and HTTP stack - would not be " +
                "installed at all; the pinned native packages also publish zero Android " +
                "wheels, and Hermes' subprocess-heavy design cannot work inside the app " +
                "sandbox under SELinux. This is a property of the audited revision, not " +
                "of Python.",
            evidence = listOf(
                "PyPI: android wheel count is 0 for cryptography, numpy, Pillow, " +
                    "sentencepiece, soundfile, brotlicffi, psutil, faster-whisper at the pins",
                "psutil is marked sys_platform != 'android' upstream",
                "uvloop omitted from [termux] because libuv configure fails on Android",
                "device b36d068a: SELinux Enforcing",
            ),
        ),
    )

    /** The candidate this audit selects for the next milestone. */
    val selected: RuntimeCandidate = RuntimeCandidate.TERMUX

    fun verdict(candidate: RuntimeCandidate): Feasibility? =
        findings.firstOrNull { it.candidate == candidate }?.verdict

    fun finding(candidate: RuntimeCandidate): FeasibilityFinding? =
        findings.firstOrNull { it.candidate == candidate }

    /**
     * True when no backend may claim Hermes is running.
     *
     * M0-007 implemented no runtime. Any backend reporting READY would be a
     * false claim, so this is the check a future milestone must flip
     * deliberately when a real transport exists.
     */
    fun hermesActuallyRuns(): Boolean = false
}
