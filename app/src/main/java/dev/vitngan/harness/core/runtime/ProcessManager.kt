package dev.vitngan.harness.core.runtime

import dev.vitngan.harness.core.workspace.CanonicalPath
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Result of a process launch request. */
sealed interface LaunchResult {

    data class Started(val processId: String, val executable: String) : LaunchResult

    /** The executable is not in the trusted allow-list. */
    data class NotAllowed(val reason: String) : LaunchResult

    /** The executable is allowed but could not be started. */
    data class Failed(val reason: String) : LaunchResult
}

/**
 * Launches authorised processes.
 *
 * Security invariants enforced by the *shape of this API*, not by runtime
 * checks:
 *
 *  - S2 this class never resolves paths. It has no [SecurityPathResolver],
 *    no root, and no way to be given one.
 *  - S3 the only overload that launches takes a [CanonicalPath]. There is no
 *    `launch(String path, ...)` to call by mistake, because [CanonicalPath]
 *    cannot be constructed outside the workspace module.
 *  - S4 commands are launched from an **argument list**. There is no shell
 *    invocation and no string concatenation into `sh -c` anywhere here.
 */
class ProcessManager(
    /** Executables the app is willing to run. Trusted configuration, never agent input. */
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
     * Launches [executable] located at an already-authorised [path].
     *
     * [args] is passed through verbatim as an argument list.
     */
    fun launch(
        executable: String,
        path: CanonicalPath,
        args: List<String> = emptyList(),
        environment: Map<String, String> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis(),
    ): LaunchResult {
        if (executable !in allowList) {
            return LaunchResult.NotAllowed(
                "executable '$executable' is not in the trusted allow-list",
            )
        }
        // The path must already be authorised; it is only re-confirmed here so
        // a path carried across workspaces cannot be reused here.
        if (!path.isExecutableLocation) {
            return LaunchResult.Failed(
                "path ${path.absolutePath} is not a file location",
            )
        }

        val processId = "proc-${running.size + 1}"
        val start = runner.start(
            executable = executable,
            workingDirectory = path,
            argumentList = args,
            environment = environment,
        )
        if (!start) {
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
}

/**
 * The seam where a real process is spawned.
 *
 * Kept as an interface so [ProcessManager] remains unit-testable on the JVM,
 * and so the argument-list discipline (S4) is visible at a single point.
 */
interface ProcessRunner {

    /** Starts using an explicit argument list. Must not invoke a shell. */
    fun start(
        executable: String,
        workingDirectory: dev.vitngan.harness.core.workspace.CanonicalPath,
        argumentList: List<String>,
        environment: Map<String, String>,
    ): Boolean

    /**
     * Default on a device where process launching is not yet implemented.
     * Refuses cleanly rather than half-working.
     */
    object Unsupported : ProcessRunner {
        override fun start(
            executable: String,
            workingDirectory: dev.vitngan.harness.core.workspace.CanonicalPath,
            argumentList: List<String>,
            environment: Map<String, String>,
        ): Boolean = false
    }
}

/** True when this canonical path looks like a file, i.e. not the workspace root. */
private val CanonicalPath.isExecutableLocation: Boolean
    get() = !isRoot