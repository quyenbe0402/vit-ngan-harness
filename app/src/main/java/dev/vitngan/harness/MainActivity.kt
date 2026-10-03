package dev.vitngan.harness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.vitngan.harness.ui.HarnessScreen
import dev.vitngan.harness.ui.HarnessTheme
import dev.vitngan.harness.ui.TaskViewModel

/**
 * The single M0 activity.
 *
 * The activity composes the UI and does nothing else: it owns no state,
 * touches no filesystem, and holds no reference to a backend or the policy
 * engine. Everything it can do is a method on [TaskViewModel].
 */
class MainActivity : ComponentActivity() {

    private val harness: TaskViewModel by viewModels { HarnessGraph.viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HarnessTheme {
                val state by harness.state.collectAsState()
                HarnessScreen(
                    state = state,
                    onCreateTask = { harness.createTask(it) },
                    onRefresh = { harness.refreshTasks() },
                    onSelectWorkspace = { harness.selectWorkspaceId(DEFAULT_WORKSPACE) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        harness.observe()
        harness.selectWorkspaceId(DEFAULT_WORKSPACE)
    }

    override fun onStop() {
        harness.stopObserving()
        super.onStop()
    }

    companion object {
        const val DEFAULT_WORKSPACE = "app-private"
    }
}