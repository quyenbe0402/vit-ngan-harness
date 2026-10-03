package dev.vitngan.probe

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

/**
 * NON-PRODUCTION diagnostic probe (M0-008J / M0-008L-F).
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

    /**
     * M0-008L-F. cryptography==50.0.1 rebuilt for Android arm64 / CPython 3.14
     * against the Chaquopy 17.0 runtime libraries.
     *
     * crypto_probe.run() raises on any failure, so a Python exception surfaces
     * as a PythonException here and fails the instrumentation test. No failure
     * path is converted into a PASS.
     */
    fun runCrypto(): String {
        if (!Python.isStarted()) Python.start(AndroidPlatform(ctx))
        val py = Python.getInstance()
        val result = py.getModule("crypto_probe")!!.get("run")!!.call()!!.toString()
        Log.i("M0_008L_F_CRYPTO", "\n" + result)
        if (!result.contains("NATIVE_OPERATION_OK")) {
            throw IllegalStateException("cryptography native op missing:\n" + result)
        }
        return result
    }

    /**
     * M0-008M. httptools==0.8.0 rebuilt for Android arm64 / CPython 3.14.
     * httptools is an Android-active Hermes core dependency reached via
     * uvicorn. httptools_probe.run() raises on every failure.
     */
    fun runHttptools(): String {
        if (!Python.isStarted()) Python.start(AndroidPlatform(ctx))
        val py = Python.getInstance()
        val result = py.getModule("httptools_probe")!!.get("run")!!.call()!!.toString()
        Log.i("M0_008M_HTTPTOOLS", "\n" + result)
        if (!result.contains("NATIVE_OPERATION_OK")) {
            throw IllegalStateException("httptools native op missing:\n" + result)
        }
        return result
    }
}

