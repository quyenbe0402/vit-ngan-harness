package dev.vitngan.harness.core.runtime

import dev.vitngan.harness.core.workspace.CanonicalPath
import java.util.concurrent.ConcurrentHashMap

/** Result of a process launch request. */
sealed interface LaunchResult {

    data class Started(val processId: String, val executable: String) : LaunchResult

    /** The executable is not in the trusted allow-list. */
    data class NotAllowed(val reason: String) : LaunchResult

    /** The path was not authorised by the workspace broker. */
    data class NotAuthorised(val reason: String) : LaunchResult

    /** The executable is allowed but could not be started. */
    data class Failed(val reason: String) : LaunchResult
}

/** Result of a supervised launch request. */
sealed interface SupervisedLaunchResult {
    /** The process was spawned. Says nothing about whether it became ready. */
    data class Started(val handle: SupervisedProcessHandle) : SupervisedLaunchResult

    /** The executable is not in the trusted allow-list. */
    data class NotAllowed(val reason: String) : SupervisedLaunchResult

    /** The path was not authorised for process execution. */
    data class NotAuthorised(val reason: String) : SupervisedLaunchResult

    /** The environment was rejected before anything was spawned. */
    data class EnvironmentRefused(val reason: String) : SupervisedLaunchResult

    /** Allowed by policy, but the process could not be started. */
    data class Failed(val reason: String) : SupervisedLaunchResult
}

/**
 * Environment policy for spawned processes (invariant S5).
 *
 * A child inherits nothing by default: the caller states the whole environment
 * it wants. That makes the environment an auditable object rather than an
 * ambient inheritance nobody reviewed.
 *
 * What is refused:
 *  - malformed keys (`=`, NUL, newline) that could smuggle a second variable;
 *  - values containing NUL, which cannot survive execve anyway;
 *  - a handful of variables whose presence in a *child* would escalate
 *    privilege or leak credentials (`LD_PRELOAD`, `LD_LIBRARY_PATH` pointing at
 *    a caller-chosen tree, `ANDROID_*` internals).
 *
 * `LD_LIBRARY_PATH` is the sharp edge here: the Termux runtime legitimately
 * needs it, so it is permitted only when the caller opts in explicitly via
 * [allowLdLibraryPath], and that choice is the caller's to justify. It is
 * recorded here rather than inferred.
 */
class EnvironmentPolicy(
    private val allowLdLibraryPath: Boolean = false,
) {
    /** Returns null when the environment is acceptable, else a refusal reason. */
    fun validate(environment: Map<String, String>): String? {
        for ((key, value) in environment) {
            if (key.isEmpty()) return "environment contains an empty variable name"
            if (key.contains('=')) return "environment variable name '$key' contains '='"
            if (key.any { it == '\u0000' || it == '\n' || it == '\r' }) {
                return "environment variable name '$key' contains a control character"
            }
            if (value.any { it == '\u0000' }) {
                return "environment variable '$key' contains a NUL byte"
            }
            if (key == "LD_PRELOAD") {
                return "LD_PRELOAD is not permitted in a spawned child"
            }
            if (key == "LD_LIBRARY_PATH" && !allowLdLibraryPath) {
                return "LD_LIBRARY_PATH requires an explicit EnvironmentPolicy opt-in"
            }
        }
        return null
    }

    /** True when [EnvironmentPolicy.validate] would accept this environment. */
    fun accepts(environment: Map<String, String>): Boolean = validate(environment) == null
}

/**
 * Launches authorised processes.
 *
 * Security invariants are enforced by the *shape of this API*, not by runtime
 * checks a caller could forget:
 *
 *  - S2 this class never resolves paths. It holds no [dev.vitngan.harness.core.workspace.SecurityPathResolver],
 *    no workspace root, and no way to be given one.
 *  - S3 the only overload that launches takes a [CanonicalPath]. There is no
 *    `launch(String, ...)` to call by mistake, because [CanonicalPath]
 *    cannot be constructed outside the workspace module.
 *  - S4 commands are launched from an **argument list**. There is no shell
 *    invocation and no string concatenation into `sh -c` anywhere here.
 *  - S1 the manager cannot bypass the workspace broker: a launch requires an
 *    authorisation token issued by the broker, not merely a path.
 *
 * M0-004 executes **no** process. The default [ProcessRunner] refuses, so a
 * launch here fails cleanly rather than spawning anything.
 */
class ProcessManager(
    /** Executables the app is willing to run. Trusted config, never agent input. */
    private val allowList: Set<String>,
    private val runner: ProcessRunner = ProcessRunner.Unsupported,
    /** Validated before every supervised spawn. Never bypassed. */
    private val environmentPolicy: EnvironmentPolicy = EnvironmentPolicy(),
) {

    private val running = ConcurrentHashMap<String, RunningProcess>()

    /** Live handles for supervised children, keyed by process id. */
    private val supervised = ConcurrentHashMap<String, SupervisedProcessHandle>()

    data class RunningProcess(
        val processId: String,
        val executable: String,
        val workingDirectory: CanonicalPath,
        val startedAtMillis: Long,
    )

    /**
     * Launches [executable] at an already-authorised [path].
     *
     * [authorised] must be a token produced by
     * [dev.vitngan.harness.core.workspace.WorkspaceBroker.authoriseForProcess];
     * a plain [CanonicalPath] from anywhere is not enough. This is what stops
     * the manager being handed a path that policy never approved.
     */
    fun launch(
        executable: String,
        authorised: dev.vitngan.harness.core.workspace.AuthorisedPath,
        args: List<String> = emptyList(),
        environment: Map<String, String> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis(),
    ): LaunchResult {
        if (executable !in allowList) {
            return LaunchResult.NotAllowed(
                "executable '$executable' is not in the trusted allow-list",
            )
        }
        val path = authorised.path
        if (path.isRoot) {
            return LaunchResult.Failed("cannot execute the workspace root")
        }
        if (!authorised.allowsExecute) {
            return LaunchResult.NotAuthorised(
                "workspace path was authorised for '${authorised.capability.name}', not for process execution",
            )
        }

        val processId = "proc-${running.size + 1}"
        val started = runner.start(
            executable = executable,
            workingDirectory = path,
            argumentList = args,
            environment = environment,
        )
        if (!started) {
            return LaunchResult.Failed("could not start '$executable'")
        }

        running[processId] = RunningProcess(
            processId = processId,
            executable = executable,
            workingDirectory = path,
            startedAtMillis = nowMillis,
        )
        return LaunchResult.Started(processId, executable)
    }

    /**
     * Launches an authorised long-running child and returns a live handle.
     *
     * This is a **second door into** [ProcessManager], not a door around it.
     * Every check [launch] performs is performed here first, in the same order:
     *
     *  1. the executable must be in the trusted allow list (S1);
     *  2. the caller's token must be a real [AuthorisedPath] minted by the
     *     workspace broker, not a bare path (S3);
     *  3. that token must have been approved for `PROCESS_EXECUTE` (S1);
     *  4. the working directory may not be the workspace root itself;
     *  5. the environment is validated by [EnvironmentPolicy] (S5).
     *
     * It then delegates to [ProcessRunner.startSupervised], which receives an
     * **argument list** and never a shell string (S4). There is no `sh -c`
     * anywhere on this path.
     *
     * A successful return means only that a process was spawned. It says
     * nothing about whether a Hermes gateway inside it became ready - readiness
     * is a protocol fact observed later, never an assumption made here.
     *
     * The method is deliberately **generic**. It knows nothing about Hermes,
     * JSON-RPC, sessions or credentials; that belongs to the runtime layer
     * above.
     */
    fun launchSupervised(
        executable: String,
        authorised: dev.vitngan.harness.core.workspace.AuthorisedPath,
        args: List<String> = emptyList(),
        environment: Map<String, String> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis(),
    ): SupervisedLaunchResult {
        // The same gate sequence as launch(), in the same order. Duplicated
        // deliberately: launch() must keep working unchanged, and sharing the
        // checks through a private helper would let a future edit to one
        // silently skip the other.
        if (executable !in allowList) {
            return SupervisedLaunchResult.NotAllowed(
                "executable '$executable' is not in the trusted allow-list",
            )
        }
        val path = authorised.path
        if (path.isRoot) {
            return SupervisedLaunchResult.Failed("cannot execute the workspace root")
        }
        if (!authorised.allowsExecute) {
            return SupervisedLaunchResult.NotAuthorised(
                "workspace path was authorised for '${authorised.capability.name}', not for process execution",
            )
        }

        // Environment is validated before anything is spawned. A rejected
        // environment is a refusal, not a sanitisation: silently dropping a
        // variable would let a caller believe it was in effect when it was not.
        val envProblem = environmentPolicy.validate(environment)
        if (envProblem != null) {
            return SupervisedLaunchResult.EnvironmentRefused(envProblem)
        }

        val handle = runner.startSupervised(
            executable = executable,
            workingDirectory = path,
            argumentList = args,
            environment = environment,
        ) ?: return SupervisedLaunchResult.Failed("could not start '$executable'")

        val processId = handle.processId
        running[processId] = RunningProcess(
            processId = processId,
            executable = executable,
            workingDirectory = path,
            startedAtMillis = nowMillis,
        )
        supervised[processId] = handle
        return SupervisedLaunchResult.Started(handle)
    }

    /** Live handles for supervised processes this manager launched. */
    fun supervisedProcesses(): List<SupervisedProcessHandle> = supervised.values.toList()

    /** The handle for [processId], or null when it is unknown or finished. */
    fun supervisedHandle(processId: String): SupervisedProcessHandle? = supervised[processId]

    /**
     * Stops a supervised process: stdin is closed first so the child can exit
     * on its own, then it is terminated if it has not.
     */
    fun stopSupervised(processId: String): Boolean {
        val handle = supervised.remove(processId) ?: return false
        running.remove(processId)
        handle.closeStdin()
        if (handle.isAlive()) handle.terminate()
        return true
    }

    /** Stops every supervised process. Safe to call more than once. */
    fun stopAllSupervised(): Int {
        val ids = supervised.keys.toList()
        ids.forEach { stopSupervised(it) }
        return ids.size
    }

    /** Forgets a supervised process once it has exited on its own. */
    fun reapExitedSupervised(): Int {
        val dead = supervised.entries.filter { !it.value.isAlive() }.map { it.key }
        dead.forEach { supervised.remove(it); running.remove(it) }
        return dead.size
    }

    fun runningProcesses(): List<RunningProcess> = running.values.toList()
    fun runningCount(): Int = running.size

    fun stop(processId: String): Boolean = running.remove(processId) != null

    fun stopAll() = running.clear()

    /** Executables this manager will run. Diagnostics only. */
    fun allowListSnapshot(): Set<String> = allowList.toSet()
}

/**
 * The seam where a real process would be spawned.
 *
 * Kept as an interface so [ProcessManager] stays unit-testable on the JVM,
 * and so the argument-list discipline (S4) is visible at a single point.
 */
interface ProcessRunner {

    /** Starts using an explicit argument list. Must not invoke a shell. */
    fun start(
        executable: String,
        workingDirectory: CanonicalPath,
        argumentList: List<String>,
        environment: Map<String, String>,
    ): Boolean

    /**
     * Starts a process and hands back a live, bidirectional handle.
     *
     * M0-008 needs stdin/stdout/stderr to speak newline-delimited JSON to a
     * long-running child, which a bare `Boolean` cannot express. This is the
     * same seam, widened - not a second, parallel execution path: the allow
     * list, the `AuthorisedPath` token and the argument-list (no shell)
     * discipline are unchanged and still enforced by [ProcessManager].
     *
     * Returns null when the process could not be started.
     */
    fun startSupervised(
        executable: String,
        workingDirectory: CanonicalPath,
        argumentList: List<String>,
        environment: Map<String, String>,
    ): SupervisedProcessHandle? = null

    /**
     * Default. M0-004 launches nothing, so every launch fails cleanly here
     * rather than half-working.
     */
    object Unsupported : ProcessRunner {
        override fun start(
            executable: String,
            workingDirectory: CanonicalPath,
            argumentList: List<String>,
            environment: Map<String, String>,
        ): Boolean = false
    }
}

/**
 * A live child process the Harness can write to and read from.
 *
 * Deliberately tiny: it exposes only what a stdio protocol needs. It is not a
 * general process API, and it grants no capability - the launch that produced
 * it already passed the allow list and an [AuthorisedPath] token.
 */
interface SupervisedProcessHandle {
    val processId: String

    /** Writes one line, appending the newline the gateway expects. */
    fun writeLine(text: String): Boolean

    /**
     * Closes stdin.
     *
     * Upstream's stdio gateway exits on stdin EOF, so this is the graceful
     * shutdown signal - not a way to abandon a live child.
     */
    fun closeStdin()

    fun isAlive(): Boolean

    /** Exit code once the process has ended; null while it is still running. */
    fun exitCode(): Int?

    /** Requests termination. */
    fun terminate()

    /** Frames the child has written to stdout. Closes when stdout ends. */
    val stdout: kotlinx.coroutines.channels.Channel<String>

    /** Diagnostics the child wrote to stderr. Never treated as protocol. */
    val stderr: kotlinx.coroutines.channels.Channel<String>
}