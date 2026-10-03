package dev.vitngan.harness.core.runtime

import dev.vitngan.harness.core.policy.AppPolicy
import dev.vitngan.harness.core.policy.Capability
import dev.vitngan.harness.core.policy.CapabilityManager
import dev.vitngan.harness.core.policy.TrustedPolicyEngine
import dev.vitngan.harness.core.workspace.AppPrivateBackend
import dev.vitngan.harness.core.workspace.AuthorisationResult
import dev.vitngan.harness.core.workspace.SecurityPathResolver
import dev.vitngan.harness.core.workspace.WorkspaceBackendType
import dev.vitngan.harness.core.workspace.WorkspaceBroker
import dev.vitngan.harness.core.workspace.WorkspaceDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * ProcessManager boundary tests (S1, S2, S3, S4).
 *
 * The point of these tests is not that launches work - M0-004 launches
 * nothing. It is that every path to a launch is refused unless policy
 * approved it.
 */
class ProcessManagerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val pkg = "dev.vitngan.harness"
    private lateinit var root: File
    private lateinit var broker: WorkspaceBroker

    @Before
    fun setUp() {
        root = temp.newFolder("ws")
        File(root, "tool.sh").writeText("#!/bin/sh\necho hi\n")
        File(root, "data.txt").writeText("payload")

        val policy = AppPolicy(pkg, Capability.entries.toSet())
        val manager = CapabilityManager(TrustedPolicyEngine({ policy }))
        broker = WorkspaceBroker(manager, SecurityPathResolver())
        broker.register(
            AppPrivateBackend(
                WorkspaceDescriptor(
                    id = "main",
                    displayName = "Main",
                    backendType = WorkspaceBackendType.APP_PRIVATE,
                    rootPath = root.canonicalPath,
                ),
            ),
        )
    }

    private fun authorise(path: String, capability: Capability): AuthorisationResult.Granted {
        val result = broker.authorise(pkg, "main", path, capability)
        assertTrue("expected grant for $path, got $result", result is AuthorisationResult.Granted)
        return result as AuthorisationResult.Granted
    }

    // ---------- allow-list (S4) ----------

    @Test
    fun `an executable outside the allow list is refused`() {
        val pm = ProcessManager(allowList = setOf("sh"))
        val token = authorise("tool.sh", Capability.PROCESS_EXECUTE)
        val result = pm.launch("rm", token.authorised)
        assertTrue(result is LaunchResult.NotAllowed)
    }

    @Test
    fun `allow list is exposed for diagnostics`() {
        assertEquals(setOf("sh"), ProcessManager(setOf("sh")).allowListSnapshot())
    }

    // ---------- default runner executes nothing (M0-004) ----------

    @Test
    fun `the default runner launches nothing`() {
        val pm = ProcessManager(allowList = setOf("sh"))
        val token = authorise("tool.sh", Capability.PROCESS_EXECUTE)
        val result = pm.launch("sh", token.authorised)

        assertTrue("M0-004 must not execute", result is LaunchResult.Failed)
        assertEquals(0, pm.runningCount())
    }

    // ---------- authorisation token (S1, S3) ----------

    @Test
    fun `a token minted for read cannot be used to execute`() {
        val pm = ProcessManager(allowList = setOf("sh"))
        val readToken = authorise("tool.sh", Capability.WORKSPACE_READ)
        val result = pm.launch("sh", readToken.authorised)

        assertTrue(result is LaunchResult.NotAuthorised)
        assertEquals(0, pm.runningCount())
    }

    @Test
    fun `policy refusing the capability yields no token at all`() {
        val denying = CapabilityManager(
            TrustedPolicyEngine({ AppPolicy.readOnly(pkg) }),
        )
        val strictBroker = WorkspaceBroker(denying, SecurityPathResolver())
        strictBroker.register(
            AppPrivateBackend(
                WorkspaceDescriptor(
                    id = "main",
                    displayName = "Main",
                    backendType = WorkspaceBackendType.APP_PRIVATE,
                    rootPath = root.canonicalPath,
                ),
            ),
        )

        val result = strictBroker.authorise(pkg, "main", "tool.sh", Capability.PROCESS_EXECUTE)
        assertTrue("policy must refuse PROCESS_EXECUTE", result is AuthorisationResult.Refused)
    }

    @Test
    fun `a traversal path yields no token`() {
        val result = broker.authorise(pkg, "main", "../escape.sh", Capability.PROCESS_EXECUTE)
        assertTrue(result is AuthorisationResult.PathRejected)
    }

    @Test
    fun `an authorised execute token is accepted by the runner seam`() {
        var startedWithArgs: List<String>? = null
        val recording = object : ProcessRunner {
            override fun start(
                executable: String,
                workingDirectory: dev.vitngan.harness.core.workspace.CanonicalPath,
                argumentList: List<String>,
                environment: Map<String, String>,
            ): Boolean {
                startedWithArgs = argumentList
                return true
            }
        }
        val pm = ProcessManager(allowList = setOf("sh"), runner = recording)
        val token = authorise("tool.sh", Capability.PROCESS_EXECUTE)

        val result = pm.launch("sh", token.authorised, args = listOf("-c", "echo hi"))

        assertTrue(result is LaunchResult.Started)
        assertEquals(listOf("-c", "echo hi"), startedWithArgs)
        assertEquals(1, pm.runningCount())
    }

    @Test
    fun `the workspace root cannot be executed`() {
        val pm = ProcessManager(allowList = setOf("sh"), runner = RecordingRunner())
        // `internal` is visible from the test source set, so the token can be
        // built directly to prove the root guard without needing a real dir.
        val rootToken = dev.vitngan.harness.core.workspace.AuthorisedPath(
            path = dev.vitngan.harness.core.workspace.CanonicalPath(
                root.canonicalPath,
                root.canonicalPath,
            ),
            capability = Capability.PROCESS_EXECUTE,
            reason = "test",
        )
        val result = pm.launch("sh", rootToken)
        assertTrue("executing the workspace root must be refused", result is LaunchResult.Failed)
        assertEquals(0, pm.runningCount())
    }

    @Test
    fun `stopping removes a tracked process`() {
        val pm = ProcessManager(allowList = setOf("sh"), runner = RecordingRunner())
        val token = authorise("tool.sh", Capability.PROCESS_EXECUTE)
        val started = pm.launch("sh", token.authorised) as LaunchResult.Started
        assertTrue(pm.stop(started.processId))
        assertEquals(0, pm.runningCount())
    }

    private class RecordingRunner : ProcessRunner {
        override fun start(
            executable: String,
            workingDirectory: dev.vitngan.harness.core.workspace.CanonicalPath,
            argumentList: List<String>,
            environment: Map<String, String>,
        ): Boolean = true
    }
}