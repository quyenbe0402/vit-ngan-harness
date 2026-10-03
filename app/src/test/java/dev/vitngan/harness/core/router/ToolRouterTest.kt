package dev.vitngan.harness.core.router

import dev.vitngan.harness.core.policy.AppPolicy
import dev.vitngan.harness.core.policy.Capability
import dev.vitngan.harness.core.policy.CapabilityManager
import dev.vitngan.harness.core.policy.TrustedPolicyEngine
import dev.vitngan.harness.core.workspace.AppPrivateBackend
import dev.vitngan.harness.core.workspace.SecurityPathResolver
import dev.vitngan.harness.core.workspace.WorkspaceBackendType
import dev.vitngan.harness.core.workspace.WorkspaceBroker
import dev.vitngan.harness.core.workspace.WorkspaceDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Router ordering and policy-gating tests (S1).
 *
 * The router must never reach a backend without policy and path defence
 * agreeing first, and an unknown tool must be refused rather than ignored.
 */
class ToolRouterTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val pkg = "dev.vitngan.harness"
    private lateinit var root: File
    private lateinit var engine: TrustedPolicyEngine
    private lateinit var capabilities: CapabilityManager
    private lateinit var broker: WorkspaceBroker
    private lateinit var router: ToolRouter

    @Before
    fun setUp() {
        root = temp.newFolder("ws")
        File(root, "a.txt").writeText("content-a")
        File(root, "sub").mkdirs()
        File(root, "sub/b.txt").writeText("content-b")

        engine = TrustedPolicyEngine({ policy })
        capabilities = CapabilityManager(engine)
        broker = WorkspaceBroker(capabilities, SecurityPathResolver())
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

        router = ToolRouter(capabilities, broker)
        router.registerExact("fs.read", Capability.WORKSPACE_READ, "path") { call, path ->
            when (val read = broker.read(call.packageName, "main", path)) {
                is dev.vitngan.harness.core.workspace.BrokerResult.Ok ->
                    ToolResult.Success(call.id, read.value)
                else -> ToolResult.Failure(call.id, "read failed", "BACKEND_ERROR")
            }
        }
        router.registerPrefix("fs.", Capability.WORKSPACE_LIST, "path") { call, _ ->
            ToolResult.Success(call.id, "listed")
        }
    }

    private var policy: AppPolicy = AppPolicy(pkg, Capability.entries.toSet())

    private fun call(tool: String, vararg args: Pair<String, String>) = ToolCall(
        id = "c-1",
        toolName = tool,
        arguments = args.toMap(),
        packageName = pkg,
    )

    @Test
    fun `exact route wins over prefix route`() {
        val result = router.route(call("fs.read", "path" to "a.txt"), "main")
        assertTrue("got: $result", result is ToolResult.Success)
        assertEquals("content-a", (result as ToolResult.Success).output)
    }

    @Test
    fun `prefix route handles a non-exact tool name`() {
        val result = router.route(call("fs.listdir", "path" to "sub"), "main")
        assertTrue("got: $result", result is ToolResult.Success)
        assertEquals("listed", (result as ToolResult.Success).output)
    }

    @Test
    fun `unknown tool is refused rather than ignored`() {
        val result = router.route(call("evil.tool", "path" to "a.txt"), "main")
        assertTrue("got: $result", result is ToolResult.Failure)
        assertEquals(ToolRouter.Codes.UNKNOWN_TOOL, (result as ToolResult.Failure).code)
    }

    @Test
    fun `missing argument is reported`() {
        val result = router.route(call("fs.read"), "main")
        assertTrue("got: $result", result is ToolResult.Failure)
        assertEquals(ToolRouter.Codes.MISSING_ARGUMENT, (result as ToolResult.Failure).code)
    }

    @Test
    fun `traversal through the router is denied by path defence`() {
        val result = router.route(call("fs.read", "path" to "../../escape.txt"), "main")
        assertTrue(result is ToolResult.Denied)
        assertTrue((result as ToolResult.Denied).code.startsWith("PATH_"))
    }

    @Test
    fun `capability denial short circuits before the workspace`() {
        policy = AppPolicy.readOnly(pkg)
        val result = router.route(call("fs.read", "path" to "a.txt"), "main")
        // readOnly still allows WORKSPACE_READ, so this must succeed.
        assertTrue("got: $result", result is ToolResult.Success)
    }

    @Test
    fun `write is denied when policy omits the capability`() {
        policy = AppPolicy.readOnly(pkg)
        router.registerExact("fs.write", Capability.WORKSPACE_WRITE, "path") { c, _ ->
            ToolResult.Success(c.id, "wrote")
        }
        val result = router.route(call("fs.write", "path" to "a.txt"), "main")
        assertTrue(result is ToolResult.Denied)
        assertEquals("DISABLED", (result as ToolResult.Denied).code)
    }

    @Test
    fun `policy tampering cannot widen privileges`() {
        // The agent supplies a path naming a policy-like file. The route is
        // read-only and the deny rule still applies to any path.
        policy = AppPolicy(
            packageName = pkg,
            enabledCapabilities = Capability.entries.toSet(),
            denyRules = listOf(
                dev.vitngan.harness.core.policy.DenyRule(
                    id = "no-policy",
                    capability = Capability.WORKSPACE_READ,
                    pathPrefix = "policy",
                    reason = "policy is not agent-readable",
                ),
            ),
        )
        val result = router.route(call("fs.read", "path" to "policy/trusted.json"), "main")
        assertTrue(result is ToolResult.Denied)
        assertEquals("RULE_DENIED", (result as ToolResult.Denied).code)
    }

    @Test
    fun `registered tools are listed`() {
        assertTrue(router.registeredTools().contains("fs.read"))
    }

    @Test
    fun `handler throwing becomes a failure result not an exception`() {
        router.registerExact("fs.boom", Capability.WORKSPACE_READ, "path") { _, _ ->
            throw IllegalStateException("boom")
        }
        val result = router.route(call("fs.boom", "path" to "a.txt"), "main")
        assertTrue("got: $result", result is ToolResult.Failure)
        assertEquals(ToolRouter.Codes.HANDLER_ERROR, (result as ToolResult.Failure).code)
    }
}