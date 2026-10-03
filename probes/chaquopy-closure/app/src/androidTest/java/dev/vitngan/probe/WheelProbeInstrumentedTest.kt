package dev.vitngan.probe

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/** NON-PRODUCTION diagnostic probe (M0-008J). */
@RunWith(AndroidJUnit4::class)
class WheelProbeInstrumentedTest {

    @Test
    fun loaderDiagnostic() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val out = WheelProbe(ctx).diag()
        println("M0_008J_DIAG_BEGIN")
        println(out)
        println("M0_008J_DIAG_END")
    }

    @Test
    fun rebuiltExtensionNativeOperation() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val out = WheelProbe(ctx).run()
        println("M0_008J_NATIVE_BEGIN")
        println(out)
        println("M0_008J_NATIVE_END")
    }
}
