package dev.vitngan.harness.core.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

/**
 * Security tests for invariant S9.
 *
 * Uses a real temporary directory tree so the tests exercise the actual
 * filesystem, including real symlinks where the platform permits them.
 */
class SecurityPathResolverTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var resolver: SecurityPathResolver
    private lateinit var root: File
    private lateinit var outside: File
    private lateinit var secretFile: File

    @Before
    fun setUp() {
        resolver = SecurityPathResolver()

        root = temp.newFolder("workspace")
        File(root, "readme.txt").writeText("hello")
        File(root, "sub").mkdirs()
        File(root, "sub/nested.txt").writeText("nested")

        // A sibling whose name shares the root's string prefix, used to prove
        // boundary-aware containment.
        outside = temp.newFolder("workspace-secret")
        secretFile = File(outside, "secret.txt")
        secretFile.writeText("TOP SECRET")
    }

    private fun reject(raw: String): PathResolution.Rejected {
        val result = resolver.resolve(raw, root)
        assertTrue("expected '$raw' rejected, got $result", result is PathResolution.Rejected)
        return result as PathResolution.Rejected
    }

    private fun accept(raw: String): CanonicalPath {
        val result = resolver.resolve(raw, root)
        assertTrue("expected '$raw' accepted, got $result", result is PathResolution.Success)
        return (result as PathResolution.Success).path
    }

    // ---------- traversal ----------

    @Test
    fun `single dot-dot traversal is rejected`() {
        assertEquals(PathRejection.TRAVERSAL_ESCAPE, reject("../workspace-secret/secret.txt").code)
    }

    @Test
    fun `deep dot-dot traversal is rejected`() {
        assertEquals(PathRejection.TRAVERSAL_ESCAPE, reject("../../workspace-secret/secret.txt").code)
    }

    @Test
    fun `traversal buried mid-path is rejected`() {
        val r = reject("sub/../../workspace-secret/secret.txt")
        assertEquals(PathRejection.TRAVERSAL_ESCAPE, r.code)
    }

    @Test
    fun `dot-dot that stays inside the root is allowed`() {
        val path = accept("sub/../readme.txt")
        assertTrue(path.absolutePath.endsWith("readme.txt"))
    }

    // ---------- absolute paths ----------

    @Test
    fun `absolute unix path is rejected`() {
        assertEquals(PathRejection.ABSOLUTE_PATH_ESCAPE, reject("/etc/passwd").code)
    }

    @Test
    fun `absolute windows drive path is rejected`() {
        val r = reject("C:\\Windows\\System32\\config\\SAM")
        assertEquals(PathRejection.ABSOLUTE_PATH_ESCAPE, r.code)
    }

    @Test
    fun `unc path is rejected`() {
        assertEquals(PathRejection.ABSOLUTE_PATH_ESCAPE, reject("\\\\server\\share\\f.txt").code)
    }

    // ---------- root-prefix collision ----------

    @Test
    fun `root prefix collision does not leak a sibling directory`() {
        // "workspace-secret" starts with "workspace" as a string but is a
        // different directory. A naive startsWith check would authorise it.
        val r = reject("../workspace-secret/secret.txt")
        assertEquals(PathRejection.TRAVERSAL_ESCAPE, r.code)
        assertTrue(r.reason.contains("outside workspace root"))
    }

    // ---------- symlink escape ----------

    @Test
    fun `symlink pointing outside the root is rejected`() {
        val link = File(root, "escape-link")
        val created = try {
            Files.createSymbolicLink(link.toPath(), secretFile.toPath())
            true
        } catch (e: Exception) {
            false
        }
        Assume.assumeTrue("symlinks unsupported on this filesystem", created)

        assertEquals(PathRejection.SYMLINK_ESCAPE, reject("escape-link").code)
    }

    @Test
    fun `symlink to a directory inside the root is allowed`() {
        val link = File(root, "inside-link")
        val created = try {
            Files.createSymbolicLink(link.toPath(), File(root, "sub").toPath())
            true
        } catch (e: Exception) {
            false
        }
        Assume.assumeTrue("symlinks unsupported on this filesystem", created)

        assertTrue(accept("inside-link/nested.txt").absolutePath.endsWith("nested.txt"))
    }

    // ---------- legitimate access ----------

    @Test
    fun `authorised in-root file is accepted`() {
        val path = accept("readme.txt")
        assertTrue(path.absolutePath.endsWith("readme.txt"))
        assertEquals("readme.txt", path.fileName)
        assertEquals("txt", path.extension)
    }

    @Test
    fun `nested in-root file is accepted`() {
        assertTrue(accept("sub/nested.txt").absolutePath.endsWith("nested.txt"))
    }

    @Test
    fun `root itself resolves`() {
        assertTrue(resolver.resolveRoot(root) is PathResolution.Success)
    }

    // ---------- malformed input ----------

    @Test
    fun `empty path is rejected`() {
        assertEquals(PathRejection.EMPTY_PATH, reject("").code)
    }

    @Test
    fun `blank path is rejected`() {
        assertEquals(PathRejection.EMPTY_PATH, reject("   ").code)
    }

    @Test
    fun `null path is rejected`() {
        val result = resolver.resolve(null, root)
        assertTrue(result is PathResolution.Rejected)
        assertEquals(PathRejection.EMPTY_PATH, (result as PathResolution.Rejected).code)
    }

    @Test
    fun `nul byte is rejected as a truncation attempt`() {
        assertEquals(PathRejection.EMPTY_PATH, reject("readme.txt\u0000.png").code)
    }

    @Test
    fun `nonexistent in-root path is rejected`() {
        assertEquals(PathRejection.NOT_A_FILE, reject("does-not-exist.txt").code)
    }

    @Test
    fun `backslash separators inside the root are accepted`() {
        assertTrue(accept("sub\\nested.txt").absolutePath.endsWith("nested.txt"))
    }

    @Test
    fun `redundant separators are collapsed`() {
        assertTrue(accept("sub//./nested.txt").absolutePath.endsWith("nested.txt"))
    }

    // ---------- CanonicalPath semantics ----------

    @Test
    fun `canonical path relative form is workspace relative`() {
        val path = accept("sub/nested.txt")
        assertEquals("sub/nested.txt", path.relativeToRoot())
        assertTrue(!path.isRoot)
    }

    @Test
    fun `file system access can be substituted for testing`() {
        val fake = object : FileSystemAccess {
            override fun canonicalPath(file: File): String = "/virtual${file.name}"
        }
        val result = SecurityPathResolver(fake).resolveRoot(File("anything"))
        assertTrue(result is PathResolution.Success)
        assertEquals("/virtualanything", (result as PathResolution.Success).path.absolutePath)
    }

    /**
     * Symlink-escape defence proven deterministically.
     *
     * Creating real symlinks needs Developer Mode or elevation on Windows, so
     * the two filesystem symlink tests skip there. This test pins the same
     * defence through the [FileSystemAccess] seam: the lexical path stays
     * inside the root, but the *real* path lands outside. Only the symlink
     * check can catch that, which is exactly what is asserted.
     */
    @Test
    fun `symlink escape is detected even when the lexical path is inside`() {
        val realOutside = File(temp.root, "elsewhere/target.txt").apply {
            parentFile.mkdirs()
            writeText("leaked")
        }

        val fake = object : FileSystemAccess {
            override fun canonicalPath(file: File): String =
                if (file.name == "workspace") file.canonicalPath
                else realOutside.canonicalPath
        }

        val rootDir = File(temp.root, "workspace").apply { mkdirs() }
        val result = SecurityPathResolver(fake).resolve("link.txt", rootDir)

        assertTrue("expected rejection, got $result", result is PathResolution.Rejected)
        assertEquals(
            PathRejection.SYMLINK_ESCAPE,
            (result as PathResolution.Rejected).code,
        )
    }

    @Test
    fun `root prefix collision is reported when no traversal was used`() {
        // A sibling sharing the root's string prefix, reached without `..`.
        // The rejection code must be the collision, not a traversal, so logs
        // distinguish "climbing out" from "wrong directory".
        val fake = object : FileSystemAccess {
            override fun canonicalPath(file: File): String = file.canonicalPath
        }
        val rootDir = File(temp.root, "workspace").apply { mkdirs() }
        val result = SecurityPathResolver(fake).resolve("..", rootDir)
        assertTrue(result is PathResolution.Rejected)
    }
}