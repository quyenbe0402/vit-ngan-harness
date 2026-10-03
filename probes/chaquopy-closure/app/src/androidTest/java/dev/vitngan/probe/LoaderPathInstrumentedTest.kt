package dev.vitngan.probe

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/** NON-PRODUCTION diagnostic probe (M0-008K). */
@RunWith(AndroidJUnit4::class)
class LoaderPathInstrumentedTest {

    @Test
    fun systemLoadVsDlopen() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val out = LoaderPathProbe(ctx).run()
        println("M0_008K_BEGIN")
        println(out)
        println("M0_008K_END")
        // Never convert a failure into a pass. The probe records every
        // outcome; the assertions live here.
        if (out.contains("FATAL")) throw IllegalStateException(out)
    }
}
