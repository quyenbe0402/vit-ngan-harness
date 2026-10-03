package dev.vitngan.probe

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import java.security.MessageDigest

/**
 * NON-PRODUCTION diagnostic probe (M0-008K).
 *
 * Tests whether Android's System.load() provides a load path under which the
 * exact pydantic-core binary that fails via libc dlopen() becomes loadable.
 * The binary is NOT modified in any way by this probe.
 */
class LoaderPathProbe(private val ctx: Context) {

    companion object {
        const val TAG = "M0_008K_LOADER"
    }

    private val lines = StringBuilder()

    private fun log(s: String) {
        lines.append(s).append("\n")
        Log.i(TAG, s)
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { st ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = st.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Locate a file by basename anywhere under the app files dir. */
    private fun findFile(name: String): File? {
        val stack = ArrayDeque<File>()
        stack.addLast(ctx.filesDir)
        var guard = 0
        while (stack.isNotEmpty() && guard < 40000) {
            guard++
            val dir = stack.removeLast()
            val kids = dir.listFiles() ?: continue
            for (f in kids) {
                if (f.isDirectory) stack.addLast(f)
                else if (f.name == name) return f
            }
        }
        return null
    }

    fun run(): String {
        log("=== M0-008K System.load vs dlopen ===")

        // ---- class loader context, observed not assumed ----
        val cls = javaClass.classLoader
        log("--- class loader context ---")
        log("  probe ClassLoader            = " + cls)
        log("  is system ClassLoader?       = " + (cls === ClassLoader.getSystemClassLoader()))
        log("  applicationInfo.nativeLibDir = " + ctx.applicationInfo.nativeLibraryDir)
        log("  filesDir                     = " + ctx.filesDir)
        log("  java.library.path            = " + System.getProperty("java.library.path"))
        log("  android.os.Build.VERSION.RELEASE = " + android.os.Build.VERSION.RELEASE)

        // ---- Python must be initialised first, this is the real runtime ----
        if (!Python.isStarted()) Python.start(AndroidPlatform(ctx))
        val py = Python.getInstance()
        val ver = py.getModule("sys").get("version")!!.toString().trim()
        log("PYTHON_RUNTIME_OK version=" + ver.substringBefore(" ["))

        // Chaquopy extracts requirement .so files lazily, on first import.
        // Trigger that first, then locate the real file.
        try {
            py.getModule("pydantic_core")
            log("  (pydantic_core imported)")
        } catch (t: Throwable) {
            // Expected to fail on the probe wheel's __init__ shim, but this is
            // what makes Chaquopy extract the .so into the app files dir.
            log("  (pydantic_core import attempted; this materialises the .so)")
        }
        val soFile = findFile("_pydantic_core.so")
        if (soFile == null) {
            log("FATAL _pydantic_core.so not found; app files tree:")
            val st = ArrayDeque<File>(); st.addLast(ctx.filesDir); var n = 0
            while (st.isNotEmpty() && n < 4000) {
                val d = st.removeLast(); n++
                (d.listFiles() ?: continue).forEach { f ->
                    if (f.isDirectory) st.addLast(f) else if (f.name.endsWith(".so")) log("    so: " + f.absolutePath)
                }
            }
            return lines.toString()
        }
        log("--- exact binary under test ---")
        log("  path    = " + soFile.absolutePath)
        log("  size    = " + soFile.length())
        log("  sha256  = " + sha256(soFile))

        // ---- Variant A: control, plain libc dlopen via Python ctypes ----
        log("--- Variant A: dlopen(path, RTLD_NOW|RTLD_LOCAL)  [CONTROL] ---")
        val pyRes = py.getModule("loader_path_diag")!!.get("run")!!.call(
            py.getModule("builtins").get("str")!!.call(soFile.absolutePath)!!)!!.toString()
        log(pyRes)

        // ---- Variant B: System.load of the same absolute path ----
        log("--- Variant B: System.load(absolutePath) ---")
        val sysLoadB = try {
            System.load(soFile.absolutePath)
            "SYSTEM_LOAD_RESULT=SUCCESS"
        } catch (t: Throwable) {
            "SYSTEM_LOAD_RESULT=FAIL ${t.javaClass.name}: ${t.message}"
        }
        log("  " + sysLoadB)

        // ---- Variant C: System.load(libpython) first, then the extension ----
        log("--- Variant C: System.load(libpython3.14.so) then System.load(extension) ---")
        val libPython = File(ctx.applicationInfo.nativeLibraryDir, "libpython3.14.so")
        val c1 = try {
            System.load(libPython.absolutePath); "libpython System.load=SUCCESS"
        } catch (t: Throwable) { "libpython System.load=FAIL ${t.javaClass.simpleName}: ${t.message}" }
        log("  " + c1)
        log("  libpython exists=" + libPython.exists() + " path=" + libPython.absolutePath)
        val c2 = try {
            System.load(soFile.absolutePath)
            "SYSTEM_LOAD_RESULT=SUCCESS"
        } catch (t: Throwable) {
            "SYSTEM_LOAD_RESULT=FAIL ${t.javaClass.name}: ${t.message}"
        }
        log("  after libpython: " + c2)

        // ---- Variant D: dlsym confirms PyLong_Type, then System.load ----
        log("--- Variant D: dlsym(PyLong_Type) then System.load(extension) ---")
        val d1 = py.getModule("loader_path_diag")!!.get("probe_symbols")!!.call()!!.toString()
        log(d1)
        val d2 = try {
            System.load(soFile.absolutePath)
            "SYSTEM_LOAD_RESULT=SUCCESS"
        } catch (t: Throwable) {
            "SYSTEM_LOAD_RESULT=FAIL ${t.javaClass.name}: ${t.message}"
        }
        log("  after dlsym: " + d2)

        // ---- Control: System.load a Chaquopy-owned extension that normally works ----
        log("--- Control: System.load(Chaquopy math extension) ---")
        val math = findFile("math.cpython-314-aarch64-linux-android.so")
        if (math == null) {
            log("  math .so NOT FOUND")
        } else {
            log("  path = " + math.absolutePath)
            log("  sha256 = " + sha256(math))
            val m1 = try {
                System.load(math.absolutePath); "math System.load=SUCCESS"
            } catch (t: Throwable) { "math System.load=FAIL ${t.javaClass.simpleName}: ${t.message}" }
            log("  " + m1)
            // and via libc dlopen for comparison
            val m2 = py.getModule("loader_path_diag")!!.get("dlopen_one")!!.call(
                py.getModule("builtins").get("str")!!.call(math.absolutePath)!!)!!.toString()
            log("  math " + m2)
        }

        // ---- Final verdict: does Python import work now? ----
        log("--- final: Python import and native operation ---")
        val finalRes = py.getModule("loader_path_diag")!!.get("final_import")!!.call()!!.toString()
        log(finalRes)
        log("=== END ===")
        return lines.toString()
    }
}
