package dev.vitngan.harness.core.runtime

import dev.vitngan.harness.core.policy.AppPolicy
import dev.vitngan.harness.core.policy.Capability
import dev.vitngan.harness.core.policy.CapabilityManager
import dev.vitngan.harness.core.policy.TrustedPolicyEngine
import dev.vitngan.harness.core.workspace.AppPrivateBackend
import dev.vitngan.harness.core.workspace.AuthorisationResult
import dev.vitngan.harness.core.workspace.AuthorisedPath
import dev.vitngan.harness.core.workspace.CanonicalPath
import dev.vitngan.harness.core.workspace.SecurityPathResolver
import dev.vitngan.harness.core.workspace.WorkspaceBackendType
import dev.vitngan.harness.core.workspace.WorkspaceBroker
import dev.vitngan.harness.core.workspace.WorkspaceDescriptor
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Security tests for `launchSupervised`.
 *
 * Behaviour, not implementation trivia: the point is that no caller can reach
 * a process spawn without the same authorisation chain `launch` already had.
 */
class ProcessManagerSupervisedLaunchTest {

    private lateinit var root: String

    private val pkg = "dev.vitngan.harness"
    private val allowList = setOf("python3", "node")

    private class RecordingRunner(
        private val handle: SupervisedProcessHandle? = FakeHandle("proc-1"),
    ) : ProcessRunner {
        var supervisedCalls = 0
        var lastArgs: List<String>? = null

        override fun start(
            executable: String,
            workingDirectory: CanonicalPath,
            argumentList: List<String>,
            environment: Map<String, String>,
        ): Boolean = true

        override fun startSupervised(
            executable: String,
            workingDirectory: CanonicalPath,
            argumentList: List<String>,
            environment: Map<String, String>,
        ): SupervisedProcessHandle? {
            supervisedCalls++
            lastArgs = argumentList
            return handle
        }
    }

    private class FakeHandle(
        override val processId: String,
        var alive: Boolean = true,
        var exit: Int? = null,
    ) : SupervisedProcessHandle {
        val written = mutableListOf<String>()
        var stdinClosed = false
        var terminated = false

        override val stdout: Channel<String> = Channel(Channel.UNLIMITED)
        override val stderr: Channel<String> = Channel(Channel.UNLIMITED)

        override fun writeLine(text: String): Boolean {
            written.add(text)
            return true
        }

        override fun closeStdin() {
            stdinClosed = true
        }

        override fun isAlive(): Boolean = alive

        override fun exitCode(): Int? = exit

        override fun terminate() {
            terminated = true
            alive = false
            exit = 143
        }
    }

    private fun broker(): WorkspaceBroker {
        val policy = AppPolicy(pkg, Capability.entries.toSet())
        val backend = AppPrivateBackend(
            WorkspaceDescriptor(
                id = "main",
                displayName = "App private",
                backendType = WorkspaceBackendType.APP_PRIVATE,
                rootPath = root,
            ),
        )
        return WorkspaceBroker(
            CapabilityManager(TrustedPolicyEngine({ policy })),
            SecurityPathResolver(),
        ).apply { register(backend) }
    }

    private fun token(relative: String, capability: Capability): AuthorisedPath {
        val result = broker().authorise(pkg, "main", relative, capability)
        assertTrue("expected authorisation for $relative, got $result", result is AuthorisationResult.Granted)
        return (result as AuthorisationResult.Granted).authorised
    }

    private fun execToken(relative: String = "tools/run.sh") = token(relative, Capability.PROCESS_EXECUTE)

    private fun readToken(relative: String = "tools/run.sh") = token(relative, Capability.WORKSPACE_READ)
    // ── allow list gates the spawn ─────────────────────────────────────────

    @Test
    fun `an allow-listed executable launches`() {
        val runner = RecordingRunner()
        val manager = ProcessManager(allowList, runner)
        assertTrue(manager.launchSupervised("python3", execToken()) is SupervisedLaunchResult.Started)
        assertEquals(1, runner.supervisedCalls)
    }

    @Test
    fun `an executable outside the allow list is refused before any spawn`() {
        val runner = RecordingRunner()
        val result = ProcessManager(allowList, runner).launchSupervised("/bin/sh", execToken())
        assertTrue(result is SupervisedLaunchResult.NotAllowed)
        assertEquals("nothing may be spawned", 0, runner.supervisedCalls)
    }

    @Test
    fun `an absolute path is not an allow-list bypass`() {
        val runner = RecordingRunner()
        val result = ProcessManager(allowList, runner)
            .launchSupervised("/data/data/com.termux/files/usr/bin/python", execToken())
        assertTrue(result is SupervisedLaunchResult.NotAllowed)
        assertEquals(0, runner.supervisedCalls)
    }

    // ── path authorisation ─────────────────────────────────────────────────

    @Test
    fun `a token authorised only for reading cannot launch a process`() {
        val result = ProcessManager(allowList, RecordingRunner())
            .launchSupervised("python3", readToken())
        assertTrue(result is SupervisedLaunchResult.NotAuthorised)
    }

    @Test
    fun `a path that escapes the workspace never yields a token`() {
        val result = broker().authorise(pkg, "main", "../../etc/passwd", Capability.PROCESS_EXECUTE)
        assertFalse("traversal must not be authorised", result is AuthorisationResult.Granted)
    }

    @Test
    fun `an absolute path never yields a token`() {
        val result = broker().authorise(pkg, "main", "/etc/shadow", Capability.PROCESS_EXECUTE)
        assertFalse(result is AuthorisationResult.Granted)
    }

    @Test
    fun `there is no overload taking a raw string working directory`() {
        // Structural: this is what makes S3 hold rather than merely be documented.
        val params = ProcessManager::class.java.methods
            .first { it.name == "launchSupervised" }
            .parameterTypes
            .map { it.name }
        assertTrue(
            "the authorised token must be in the signature: $params",
            params.any { it.contains("AuthorisedPath") },
        )
        assertEquals(
            "only the executable name may be a String: $params",
            1,
            params.count { it == "java.lang.String" },
        )
    }

    @Test
    fun `the workspace root itself is refused as a working directory`() {
        val granted = broker().authorise(pkg, "main", ".", Capability.PROCESS_EXECUTE)
        assertTrue(granted is AuthorisationResult.Granted)
        val result = ProcessManager(allowList, RecordingRunner())
            .launchSupervised("python3", (granted as AuthorisationResult.Granted).authorised)
        assertTrue(result is SupervisedLaunchResult.Failed)
    }

    // ── argv discipline, never a shell ─────────────────────────────────────

    @Test
    fun `arguments stay discrete argv entries`() {
        val runner = RecordingRunner()
        ProcessManager(allowList, runner).launchSupervised(
            "python3",
            execToken(),
            args = listOf("-m", "tui_gateway.entry", "--flag", "value with spaces"),
        )
        assertEquals(
            listOf("-m", "tui_gateway.entry", "--flag", "value with spaces"),
            runner.lastArgs,
        )
    }

    @Test
    fun `no shell ever appears in argv`() {
        val runner = RecordingRunner()
        ProcessManager(allowList, runner).launchSupervised("node", execToken(), args = listOf("-e", "x"))
        assertFalse(runner.lastArgs!!.any { it == "sh" || it == "bash" || it == "-c" })
    }

    @Test
    fun `shell metacharacters are carried literally, not re-parsed`() {
        val runner = RecordingRunner()
        val hostile = "; rm -rf / #"
        ProcessManager(allowList, runner).launchSupervised("node", execToken(), args = listOf(hostile))
        assertEquals(listOf(hostile), runner.lastArgs)
    }
    // ── failure and exit tracking ──────────────────────────────────────────

    @Test
    fun `a runner that cannot start is surfaced as Failed`() {
        val manager = ProcessManager(allowList, RecordingRunner(handle = null))
        assertTrue(manager.launchSupervised("python3", execToken()) is SupervisedLaunchResult.Failed)
    }

    @Test
    fun `the default runner still refuses everything`() {
        assertTrue(
            ProcessManager(allowList).launchSupervised("python3", execToken())
                is SupervisedLaunchResult.Failed,
        )
    }

    @Test
    fun `process exit is tracked and the dead child can be reaped`() {
        val handle = FakeHandle("proc-9")
        val manager = ProcessManager(allowList, RecordingRunner(handle))
        manager.launchSupervised("python3", execToken())
        assertNotNull(manager.supervisedHandle("proc-9"))

        handle.alive = false
        handle.exit = 0
        assertEquals(1, manager.reapExitedSupervised())
        assertNull(manager.supervisedHandle("proc-9"))
        assertEquals(0, manager.runningCount())
    }

    // ── cancellation and shutdown ──────────────────────────────────────────

    @Test
    fun `stopping closes stdin first, then terminates`() {
        val handle = FakeHandle("proc-2")
        val manager = ProcessManager(allowList, RecordingRunner(handle))
        manager.launchSupervised("python3", execToken())

        assertTrue(manager.stopSupervised("proc-2"))
        assertTrue("stdin EOF is the graceful shutdown signal", handle.stdinClosed)
        assertTrue(handle.terminated)
        assertNull(manager.supervisedHandle("proc-2"))
    }

    @Test
    fun `stopping an unknown process is a safe no-op`() {
        val manager = ProcessManager(allowList, RecordingRunner())
        assertFalse(manager.stopSupervised("nope"))
        assertEquals(0, manager.stopAllSupervised())
    }

    @Test
    fun `a launch never reports readiness`() {
        // A child being spawned says nothing about a gateway inside it becoming
        // ready. There is deliberately no readiness anywhere on this result.
        val members = SupervisedLaunchResult::class.java.methods.map { it.name }
        assertFalse(
            "no readiness may be reported here: $members",
            members.any { it.contains("eady", ignoreCase = true) },
        )
    }

    // ── observability ──────────────────────────────────────────────────────

    @Test
    fun `stdout and stderr are observable through the handle`() {
        val handle = FakeHandle("proc-3")
        handle.stdout.trySend("{\"line\":1}")
        handle.stderr.trySend("a warning")
        val manager = ProcessManager(allowList, RecordingRunner(handle))
        manager.launchSupervised("python3", execToken())
        val live = manager.supervisedHandle("proc-3")!!
        assertEquals("{\"line\":1}", live.stdout.tryReceive().getOrNull())
        assertEquals("a warning", live.stderr.tryReceive().getOrNull())
    }

    @Test
    fun `writes reach the child as lines`() {
        val handle = FakeHandle("proc-4")
        val manager = ProcessManager(allowList, RecordingRunner(handle))
        val started = manager.launchSupervised("python3", execToken())
            as SupervisedLaunchResult.Started
        started.handle.writeLine("{\"jsonrpc\":\"2.0\"}")
        assertEquals(listOf("{\"jsonrpc\":\"2.0\"}"), handle.written)
    }
    // ── environment policy ─────────────────────────────────────────────────

    @Test
    fun `an ordinary environment is accepted`() {
        val result = ProcessManager(allowList, RecordingRunner()).launchSupervised(
            "python3",
            execToken(),
            environment = mapOf("PATH" to "/usr/bin", "HOME" to "/home/x"),
        )
        assertTrue(result is SupervisedLaunchResult.Started)
    }

    @Test
    fun `LD_PRELOAD is refused before any spawn`() {
        val runner = RecordingRunner()
        val result = ProcessManager(allowList, runner).launchSupervised(
            "python3",
            execToken(),
            environment = mapOf("LD_PRELOAD" to "/tmp/evil.so"),
        )
        assertTrue(result is SupervisedLaunchResult.EnvironmentRefused)
        assertEquals(0, runner.supervisedCalls)
    }

    @Test
    fun `LD_LIBRARY_PATH requires an explicit opt-in`() {
        val refused = ProcessManager(allowList, RecordingRunner()).launchSupervised(
            "python3",
            execToken(),
            environment = mapOf("LD_LIBRARY_PATH" to "/data/data/com.termux/files/usr/lib"),
        )
        assertTrue(refused is SupervisedLaunchResult.EnvironmentRefused)

        val allowing = ProcessManager(
            allowList,
            RecordingRunner(),
            EnvironmentPolicy(allowLdLibraryPath = true),
        ).launchSupervised(
            "python3",
            execToken(),
            environment = mapOf("LD_LIBRARY_PATH" to "/data/data/com.termux/files/usr/lib"),
        )
        assertTrue(allowing is SupervisedLaunchResult.Started)
    }

    @Test
    fun `a malformed variable name is refused, not sanitised`() {
        val runner = RecordingRunner()
        val result = ProcessManager(allowList, runner).launchSupervised(
            "python3",
            execToken(),
            environment = mapOf("GOOD" to "1", "BAD=INJECTED" to "2"),
        )
        assertTrue(result is SupervisedLaunchResult.EnvironmentRefused)
        assertEquals("nothing spawned", 0, runner.supervisedCalls)
    }

    @Test
    fun `a NUL byte in a value is refused`() {
        val result = ProcessManager(allowList, RecordingRunner()).launchSupervised(
            "python3",
            execToken(),
            environment = mapOf("X" to "a\u0000b"),
        )
        assertTrue(result is SupervisedLaunchResult.EnvironmentRefused)
    }

    @Test
    fun `the environment policy makes the same decision standalone`() {
        assertTrue(EnvironmentPolicy().accepts(mapOf("PATH" to "/usr/bin")))
        assertFalse(EnvironmentPolicy().accepts(mapOf("LD_PRELOAD" to "x")))
        assertFalse(EnvironmentPolicy().accepts(mapOf("A\nB" to "1")))
    }

    // ── ProcessManager holds no policy authority ───────────────────────────

    @Test
    fun `process manager owns no policy engine`() {
        val fields = ProcessManager::class.java.declaredFields.map { it.type.simpleName }
        assertFalse(fields.contains("TrustedPolicyEngine"))
        assertFalse(fields.contains("CapabilityManager"))
        assertFalse(fields.contains("WorkspaceBroker"))
    }

    @Test
    fun `launch and launchSupervised share one allow list`() {
        val manager = ProcessManager(allowList, RecordingRunner())
        assertTrue(manager.launch("python3", execToken()) is LaunchResult.Started)
        assertTrue(manager.launch("curl", execToken()) is LaunchResult.NotAllowed)
        assertTrue(
            manager.launchSupervised("curl", execToken()) is SupervisedLaunchResult.NotAllowed,
        )
    }

    @get:Rule
    val workspaceRoot = TemporaryFolder()

    @Before
    fun createFixture() {
        // The broker resolves real files and re-checks containment, so the
        // fixture must exist on a real temporary root - exactly as the other
        // workspace tests in this project already do.
        val tools = workspaceRoot.newFolder("tools")
        java.io.File(tools, "run.sh").writeText("#!/system/bin/sh")
        root = workspaceRoot.root.canonicalPath
    }
}