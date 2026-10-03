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
) {

    private val running = ConcurrentHashMap<String, RunningProcess>()

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