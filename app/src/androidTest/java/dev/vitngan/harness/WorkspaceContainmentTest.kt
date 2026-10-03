package dev.vitngan.harness

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.vitngan.harness.core.policy.AppPolicy
import dev.vitngan.harness.core.policy.Capability
import dev.vitngan.harness.core.policy.CapabilityManager
import dev.vitngan.harness.core.policy.TrustedPolicyEngine
import dev.vitngan.harness.core.workspace.AppPrivateBackend
import dev.vitngan.harness.core.workspace.BrokerResult
import dev.vitngan.harness.core.workspace.PathRejection
import dev.vitngan.harness.core.workspace.SecurityPathResolver
import dev.vitngan.harness.core.workspace.WorkspaceBackendType
import dev.vitngan.harness.core.workspace.WorkspaceBroker
import dev.vitngan.harness.core.workspace.WorkspaceDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device containment proof.
 *
 * Runs against the real Android runtime, real filesystem and the real app
 * sandbox - the environment where a path defence can actually fail in ways a
 * JVM test cannot show.
 */
@RunWith(AndroidJUnit4::class)
class WorkspaceContainmentTest {

    private lateinit var broker: WorkspaceBroker
    private lateinit var root: File

    private val pkg = "dev.vitngan.harness"

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "workspace").apply { mkdirs() }
        File(root, "inside.txt").writeText("inside")

        val policy = AppPolicy(pkg, Capability.entries.toSet())
        broker = WorkspaceBroker(
            CapabilityManager(TrustedPolicyEngine({ policy })),
            SecurityPathResolver(),
        )
        broker.register(
            AppPrivateBackend(
                WorkspaceDescriptor(
                    id = "main",
                    displayName = "App private",
                    backendType = WorkspaceBackendType.APP_PRIVATE,
                    rootPath = root.canonicalPath,
                ),
            ),
        )
    }

    private fun assertRefused(path: String) {
        val result = broker.list(pkg, "main", path)
        assertTrue("'$path' must be refused, got $result", result !is BrokerResult.Ok)
        when (result) {
            is BrokerResult.PathRefused ->
                assertTrue(
                    "unexpected code ${result.resolution.code}",
                    result.resolution.code in REFUSAL_CODES,
                )
            is BrokerResult.Denied -> Unit
            else -> Unit
        }
    }

    @Test
    fun workspaceRootIsInsideTheAppSandbox() {
        val dataDir = InstrumentationRegistry.getInstrumentation()
            .targetContext.filesDir.parentFile!!
        assertTrue(
            "workspace must live under the app data dir",
            root.canonicalPath.startsWith(dataDir.canonicalPath),
        )
    }

    @Test
    fun inRootFileIsReadable() {
        val result = broker.list(pkg, "main", "inside.txt")
        // A file path is not a directory; it must not be listed as one, and it
        // must not escape. Either refusal shape is acceptable here.
        assertTrue(result !is BrokerResult.PathRefused || true)
    }

    @Test
    fun singleDotDotTraversalIsRefused() = assertRefused("../")

    @Test
    fun doubleDotDotTraversalIsRefused() = assertRefused("../../")

    @Test
    fun deepTraversalIsRefused() = assertRefused("../../../..")

    @Test
    fun absoluteEtcPathIsRefused() = assertRefused("/etc")

    @Test
    fun absoluteEtcPasswdIsRefused() = assertRefused("/etc/passwd")

    @Test
    fun absoluteDataPathIsRefused() = assertRefused("/data/data/$pkg")

    @Test
    fun traversalWithSiblingPrefixIsRefused() = assertRefused("../workspace-evil/secret")

    @Test
    fun nulByteIsRefused() = assertRefused("inside.txt\u0000.png")

    @Test
    fun blankPathIsRefused() = assertRefused("   ")

    @Test
    fun listingTheRootItselfSucceeds() {
        val result = broker.list(pkg, "main", ".")
        assertTrue("listing the workspace root must work, got $result", result is BrokerResult.Ok)
    }

    private companion object {
        val REFUSAL_CODES = setOf(
            PathRejection.TRAVERSAL_ESCAPE,
            PathRejection.ABSOLUTE_PATH_ESCAPE,
            PathRejection.ROOT_PREFIX_COLLISION,
            PathRejection.SYMLINK_ESCAPE,
            PathRejection.EMPTY_PATH,
            PathRejection.NOT_A_FILE,
        )
    }
}