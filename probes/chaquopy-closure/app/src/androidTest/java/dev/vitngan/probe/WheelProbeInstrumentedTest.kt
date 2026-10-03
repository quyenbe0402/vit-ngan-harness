package dev.vitngan.probe

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/** NON-PRODUCTION feasibility probe (M0-008I). */
@RunWith(AndroidJUnit4::class)
class WheelProbeInstrumentedTest {
    @Test
    fun rebuiltWheelLoadsAndOperates() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val res = WheelProbe(ctx).run()
        println("M0_008I_RESULT_BEGIN")
        println(res)
        println("M0_008I_RESULT_END")
    }
}