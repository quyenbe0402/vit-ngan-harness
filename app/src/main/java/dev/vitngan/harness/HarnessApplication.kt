package dev.vitngan.harness

import android.app.Application

/**
 * Holds the process-wide graph.
 *
 * Only the graph touches the filesystem, the policy engine or a backend.
 */
class HarnessApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Built eagerly so a configuration fault surfaces at startup rather
        // than on first user interaction.
        HarnessGraph.container(this)
    }

    companion object {
        @Volatile
        var instance: HarnessApplication? = null
            private set
    }
}