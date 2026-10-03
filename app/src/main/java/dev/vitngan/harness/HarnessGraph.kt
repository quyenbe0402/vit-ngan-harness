package dev.vitngan.harness

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.vitngan.harness.core.event.EventBus
import dev.vitngan.harness.core.event.EventBusImpl
import dev.vitngan.harness.core.policy.AppPolicy
import dev.vitngan.harness.core.policy.Capability
import dev.vitngan.harness.core.policy.CapabilityManager
import dev.vitngan.harness.core.policy.TrustedPolicyEngine
import dev.vitngan.harness.core.task.TaskManager
import dev.vitngan.harness.core.task.TaskManagerImpl
import dev.vitngan.harness.core.workspace.AppPrivateBackend
import dev.vitngan.harness.core.workspace.SecurityPathResolver
import dev.vitngan.harness.core.workspace.WorkspaceBackend
import dev.vitngan.harness.core.workspace.WorkspaceBackendType
import dev.vitngan.harness.core.workspace.WorkspaceBroker
import dev.vitngan.harness.core.workspace.WorkspaceDescriptor
import dev.vitngan.harness.ui.TaskViewModel
import java.io.File

/**
 * Composition root.
 *
 * The one place allowed to know how the pieces are wired. Everything else
 * receives its dependencies.
 *
 * The app-private workspace root is `filesDir/workspace`, which is inside
 * the application sandbox. That containment is enforced again by
 * [SecurityPathResolver] and [AppPrivateBackend]; the path being inside the
 * sandbox is the first line, not the only one.
 */
object HarnessGraph {

    const val WORKSPACE_ID = "app-private"

    @Volatile
    private var container: Container? = null

    class Container(
        val eventBus: EventBus,
        val taskManager: TaskManager,
        val workspaceBroker: WorkspaceBroker,
    )

    fun container(context: Context): Container = container ?: synchronized(this) {
        container ?: build(context.applicationContext).also { container = it }
    }

    private fun build(app: Context): Container {
        val workspaceRoot = File(app.filesDir, "workspace").apply { mkdirs() }

        // Read-only by default. Widening to write or process execution is a
        // deliberate policy change, not something the UI can ask for.
        val policy = AppPolicy(
            packageName = MainActivity::class.java.packageName ?: "dev.vitngan.harness",
            enabledCapabilities = setOf(
                Capability.WORKSPACE_READ,
                Capability.WORKSPACE_LIST,
                Capability.CONTEXT_READ,
            ),
        )

        val capabilityManager = CapabilityManager(TrustedPolicyEngine({ policy }))
        val broker = WorkspaceBroker(capabilityManager, SecurityPathResolver())
        val backend: WorkspaceBackend = AppPrivateBackend(
            WorkspaceDescriptor(
                id = WORKSPACE_ID,
                displayName = "App private",
                backendType = WorkspaceBackendType.APP_PRIVATE,
                rootPath = workspaceRoot.canonicalPath,
                readOnly = false,
            ),
        )
        broker.register(backend)

        val tasks = TaskManagerImpl()
        val events: EventBus = EventBusImpl()

        return Container(events, tasks, broker)
    }

    /** ViewModel factory wiring [TaskViewModel] to the graph. */
    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = HarnessApplication.instance
                ?: error("HarnessApplication is not initialised")
            val c = container(app)
            return TaskViewModel(
                taskManager = c.taskManager,
                workspaceBroker = c.workspaceBroker,
                eventBus = c.eventBus,
            ) as T
        }
    }
}