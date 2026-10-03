package dev.vitngan.harness

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M0-008A: characterises the Android -> Termux IPC boundary on a real device.
 *
 * An audit, not an integration. It establishes what the boundary can and
 * cannot do so the runtime design rests on observation rather than on the
 * assumption that an intent can carry a stream.
 */
@RunWith(AndroidJUnit4::class)
class TermuxIpcBoundaryTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val termuxInstalled: Boolean
        get() = runCatching { context.packageManager.getPackageInfo("com.termux", 0) }.isSuccess

    private fun canRead(dir: java.io.File): Boolean = runCatching { dir.list() != null }.getOrDefault(false)

    @Test
    fun termuxIsInstalled() {
        assertTrue("M0-008A needs Termux present", termuxInstalled)
    }

    @Test
    fun harnessCannotReadTheTermuxSandboxDirectly() {
        // This is the whole reason a bridge is required. Asserted, not assumed.
        val bin = java.io.File("/data/data/com.termux/files/usr/bin")
        assertFalse(
            "the Harness must NOT be able to enumerate the Termux sandbox",
            canRead(bin),
        )
        assertFalse(
            "nor execute its interpreter",
            java.io.File("/data/data/com.termux/files/usr/bin/python3").canExecute(),
        )
    }

    @Test
    fun runCommandServiceIsResolvableFromTheHarness() {
        val resolved = context.packageManager
            .resolveService(Intent("com.termux.RUN_COMMAND").setPackage("com.termux"), 0)
        assertNotNull(
            "Termux 0.118.3 must expose com.termux/.app.RunCommandService",
            resolved,
        )
    }

    @Test
    fun runCommandIsEitherAcceptedOrExplicitlyRefused() {
        // A harmless probe. Both outcomes are recorded evidence; neither is
        // treated as success by fiat.
        val intent = Intent("com.termux.RUN_COMMAND").setPackage("com.termux").apply {
            // Termux 0.118.3 requires an explicit absolute path to an executable
            // inside its own sandbox; it will not take a bare argument string.
            putExtra(
                "com.termux.RUN_COMMAND_PATH",
                "/data/data/com.termux/files/usr/bin/sh",
            )
            putExtra("com.termux.RUN_COMMAND.arguments", "-c \"echo IPC_PROBE > probe.txt\"")
            putExtra("com.termux.RUN_COMMAND.workingDirectory", "/data/data/com.termux/files/home")
        }
        val outcome = runCatching { context.startService(intent) }
        val accepted = outcome.isSuccess
        val refusedSecurity = outcome.exceptionOrNull() is SecurityException

        assertTrue(
            "RUN_COMMAND must be either accepted or refused with SecurityException; " +
                "was accepted=$accepted refused=$refusedSecurity err=${outcome.exceptionOrNull()}",
            accepted || refusedSecurity,
        )
    }

    @Test
    fun termuxExposesNoStreamingCapableIpcSurface() {
        // Termux 0.118.3 declares exactly one callable service. If a future
        // version adds a streaming surface, this test starts failing and the
        // audit should be revisited.
        val services = context.packageManager
            .queryIntentServices(Intent().setPackage("com.termux"), 0)
        val callable = services.map { it.serviceInfo.name }.filter { it.contains("Service") }
        assertTrue(
            "unexpected new callable service(s): $callable",
            callable.none { it.contains("Stream") || it.contains("Socket") || it.contains("Pipe") },
        )
    }
}
