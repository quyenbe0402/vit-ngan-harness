package dev.vitngan.termuxbridge

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * M0-008F prototype bridge.
 *
 * Lives in Termux' shared UID, so it executes in the Termux sandbox - the only
 * context that can run Hermes. It exposes only fixed, structured operations:
 * there is deliberately no method taking a command string or a path.
 *
 * Caller authorisation is layered. The permission is only the outer gate; since
 * Termux' signing key is public, shared-UID membership and signature match prove
 * nothing about who is calling. The service therefore pins the expected package
 * and UID and rejects anything else before spawning anything.
 */
class BridgeService : Service() {

    private val binder = object : Binder() {
        fun createTestSession(): Bundle = handleCreate()
        fun closeTestSession(sessionId: String): Boolean = handleClose(sessionId)
        fun sessionState(sessionId: String): String = handleState(sessionId)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun authorised(): Boolean {
        val callingUid = Binder.getCallingUid()
        if (callingUid == android.os.Process.myUid()) return false
        // Resolve the UID to its packages and pin the expected one. This is
        // stronger than reading a package name off the Binder directly: it
        // proves the caller actually owns the UID it claims.
        val packages = packageManager.getPackagesForUid(callingUid) ?: return false
        return packages.contains(ALLOWED_PACKAGE)
    }

    private class Session(
        val token: String,
        val child: Process,
        val stdin: ParcelFileDescriptor,
        val stdout: ParcelFileDescriptor,
        val stderr: ParcelFileDescriptor,
        @Volatile var state: String = "RUNNING",
    )

    private val sessions = ConcurrentHashMap<String, Session>()

    private fun handleCreate(): Bundle {
        if (!authorised()) throw SecurityException("caller is not the authorised Harness")

        val script = File("/data/data/com.termux/files/home/m008f_child.py")
        if (!script.exists()) {
            return Bundle().apply { putString("error", "child script not provisioned") }
        }

        val process = ProcessBuilder(
            "/data/data/com.termux/files/usr/bin/python3",
            script.absolutePath,
        ).redirectErrorStream(false).start()

        // createPipe() is unidirectional: three separate channels, never a
        // single "duplex" pipe.
        val stdin = ParcelFileDescriptor.createPipe()[0]
        val outPipe = ParcelFileDescriptor.createPipe()
        val errPipe = ParcelFileDescriptor.createPipe()

        val session = session_of(process, stdin, outPipe, errPipe)
        val id = sessions.entries.first { it.value === session }.key

        return Bundle().apply {
            putString("sessionId", id)
            putString("token", session.token)
            putInt("childPid", 0)
        }
    }

    private fun session_of(
        process: Process,
        stdin: ParcelFileDescriptor,
        outPipe: Array<ParcelFileDescriptor>,
        errPipe: Array<ParcelFileDescriptor>,
    ): Session {
        val id = UUID.randomUUID().toString()
        val session = Session(
            UUID.randomUUID().toString(), process, stdin, outPipe[1], errPipe[1],
        )
        sessions[id] = session

        Thread {
            try {
                val fromHarness = ParcelFileDescriptor.AutoCloseInputStream(session.stdin)
                val toChild = process.outputStream
                val buf = ByteArray(4096)
                while (true) {
                    val n = fromHarness.read(buf, 0, buf.size)
                    if (n <= 0) break
                    toChild.write(buf, 0, n)
                    toChild.flush()
                }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true }.start()

        Thread {
            try {
                val fromChild = process.inputStream
                val toHarness = ParcelFileDescriptor.AutoCloseOutputStream(outPipe[0])
                val buf = ByteArray(4096)
                while (true) {
                    val n = fromChild.read(buf, 0, buf.size)
                    if (n < 0) break
                    toHarness.write(buf, 0, n)
                    toHarness.flush()
                }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true }.start()

        Thread {
            try {
                val fromChild = process.errorStream
                val toHarness = ParcelFileDescriptor.AutoCloseOutputStream(errPipe[0])
                val buf = ByteArray(4096)
                while (true) {
                    val n = fromChild.read(buf, 0, buf.size)
                    if (n < 0) break
                    toHarness.write(buf, 0, n)
                    toHarness.flush()
                }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true }.start()

        return session
    }

    private fun handleClose(sessionId: String): Boolean {
        if (!authorised()) throw SecurityException("caller is not the authorised Harness")
        val session = sessions.remove(sessionId) ?: return false
        session.state = "STOPPED"
        runCatching { session.stdin.close() }
        runCatching { session.stdout.close() }
        runCatching { session.stderr.close() }
        runCatching { session.child.destroy() }
        session.child.waitFor(3, TimeUnit.SECONDS)
        return true
    }

    private fun handleState(sessionId: String): String {
        if (!authorised()) throw SecurityException("caller is not the authorised Harness")
        val session = sessions[sessionId] ?: return "UNKNOWN"
        if (!session.child.isAlive) session.state = "EXITED"
        return session.state
    }

    private companion object {
        const val ALLOWED_PACKAGE = "dev.vitngan.harness"
    }
}
