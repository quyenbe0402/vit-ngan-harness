package dev.vitngan.probe

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

/**
 * NON-PRODUCTION diagnostic probe (M0-008J).
 * Reports the Chaquopy native loader contract and then exercises the
 * self-rebuilt extension. Diagnostic experiment only.
 */
class WheelProbe(private val ctx: Context) {

    fun diag(): String {
        if (!Python.isStarted()) Python.start(AndroidPlatform(ctx))
        val py = Python.getInstance()
        val res = py.getModule("loader_diag")!!.get("run")!!.call()!!.toString()
        Log.i("M0_008J_LOADER", "\n" + res)
        return res
    }

    fun run(): String {
        if (!Python.isStarted()) Python.start(AndroidPlatform(ctx))
        val py = Python.getInstance()
        val mod = py.getModule("probe_native")
        val result = mod.get("run")!!.call()!!.toString()
        // Fail loudly. A swallowed ImportError would make this probe look
        // green while proving nothing.
        if (result.contains("IMPORT=FAIL")) {
            throw IllegalStateException("rebuilt extension did not load:\n" + result)
        }
        if (!result.contains("NATIVE_OP=OK")) {
            throw IllegalStateException("native operation did not succeed:\n" + result)
        }
        return result
    }
}
