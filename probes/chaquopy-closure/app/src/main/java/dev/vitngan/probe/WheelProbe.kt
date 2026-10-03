package dev.vitngan.probe

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

/**
 * NON-PRODUCTION feasibility probe (M0-008I).
 * Loads a self-rebuilt Android arm64 cp314 wheel and runs a real native
 * operation inside it, not merely an import.
 */
class WheelProbe(private val ctx: Context) {

    fun run(): String {
        if (!Python.isStarted()) Python.start(AndroidPlatform(ctx))
        val py = Python.getInstance()
        // Load the real probe module shipped in the APK, so this exercises
        // module resolution and native loading the same way Hermes would.
        val mod = py.getModule("probe_native")
        val result = mod.get("run")!!.call()!!.toString()
        // Fail loudly: a swallowed ImportError would make this probe look
        // green while proving nothing.
        if (result.contains("IMPORT=FAIL")) {
            throw IllegalStateException("rebuilt wheel did not load:\n" + result)
        }
        if (!result.contains("NATIVE_OP=OK")) {
            throw IllegalStateException("native operation did not succeed:\n" + result)
        }
        return result
    }
}
