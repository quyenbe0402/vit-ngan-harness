package dev.vitngan.harness.ui

import dev.vitngan.harness.core.event.EventBus
import dev.vitngan.harness.core.event.EventBusImpl
import dev.vitngan.harness.core.event.EventEnvelope
import dev.vitngan.harness.core.event.EventFilter
import dev.vitngan.harness.core.report.ActivityMapper
import dev.vitngan.harness.core.report.ExecutionSummary
import dev.vitngan.harness.core.task.Task
import dev.vitngan.harness.core.task.TaskManager
import dev.vitngan.harness.core.task.TaskStatus
import dev.vitngan.harness.core.workspace.BrokerResult
import dev.vitngan.harness.core.workspace.WorkspaceBroker
import dev.vitngan.harness.core.workspace.WorkspaceDescriptor
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One row in the activity feed. */
data class ActivityItem(
    val id: String,
    val timestampMillis: Long,
    val type: String,
    val label: String,
    val summary: ExecutionSummary,
    val isViolation: Boolean,
)

/** One file in the workspace listing. */
data class WorkspaceFileUi(
    val name: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
)

/** Everything the UI renders. */
data class TaskUiState(
    val workspaceName: String? = null,
    val tasks: List<Task> = emptyList(),
    val activity: List<ActivityItem> = emptyList(),
    val files: List<WorkspaceFileUi> = emptyList(),
    val lastError: String? = null,
    val busy: Boolean = false,
) {
    val hasWorkspace: Boolean get() = workspaceName != null
    val violationCount: Int get() = activity.count { it.isViolation }
}

/**
 * The single source of UI state.
 *
 * Architecture rules this class exists to enforce:
 *  - the UI **never** touches the filesystem, a backend, the policy engine or
 *    the database. It talks only to [TaskManager], [WorkspaceBroker] and
 *    [EventBus] through this class.
 *  - task state is read from [TaskManager]; it is never mirrored or
 *    duplicated here.
 *  - workspace reads go through [WorkspaceBroker], so every listing is
 *    policy-checked and path-checked.
 *
 * There is deliberately no approve/reject API. Permitted actions execute
 * autonomously; the UI observes and explains.
 */
class TaskViewModel(
    private val taskManager: TaskManager,
    private val workspaceBroker: WorkspaceBroker,
    private val eventBus: EventBus = EventBusImpl(),
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(TaskUiState())
    val state: StateFlow<TaskUiState> = _state.asStateFlow()

    private var subscription: dev.vitngan.harness.core.event.Subscription? = null

    /** Subscribes to activity. Idempotent. */
    fun observe() {
        if (subscription != null) return
        subscription = eventBus.subscribe(EventFilter()) { event -> onEvent(event) }
        refreshTasks()
    }

    fun stopObserving() {
        subscription?.unsubscribe()
        subscription = null
    }

    private fun onEvent(event: EventEnvelope) {
        val item = ActivityItem(
            id = event.id,
            timestampMillis = event.timestampMillis,
            type = event.type,
            label = ActivityMapper.label(event.type),
            summary = ActivityMapper.toSummary(event),
            isViolation = ActivityMapper.isViolation(event.type),
        )
        _state.value = _state.value.copy(
            activity = (_state.value.activity + item).takeLast(MAX_ACTIVITY),
        )
        // Task state changes arrive as events; the authoritative value is
        // always re-read from the manager rather than patched from the event.
        if (event.type.startsWith("task.")) refreshTasks()
    }

    // ---------- tasks ----------

    fun refreshTasks() {
        _state.value = _state.value.copy(tasks = taskManager.all())
    }

    fun createTask(title: String, description: String = "") {
        if (title.isBlank()) {
            _state.value = _state.value.copy(lastError = "Task title must not be blank")
            return
        }
        _state.value = _state.value.copy(lastError = null, busy = true)
        val task = taskManager.create(title, description, _state.value.workspaceName)
        eventBus.publish(
            EventEnvelope(
                id = "evt-${clock()}-create",
                type = "task.created",
                timestampMillis = clock(),
                source = SOURCE,
                payload = mapOf("subject" to task.title),
            ),
        )
        refreshTasks()
        _state.value = _state.value.copy(busy = false)
    }

    fun transitionTask(id: String, next: TaskStatus) {
        val before = taskManager.get(id)
        val updated = taskManager.transition(id, next)
        if (updated == null) {
            _state.value = _state.value.copy(
                lastError = "Illegal transition: ${before?.status} -> $next",
            )
            return
        }
        _state.value = _state.value.copy(lastError = null)
        eventBus.publish(
            EventEnvelope(
                id = "evt-${clock()}-transition",
                type = "task.transition",
                timestampMillis = clock(),
                source = SOURCE,
                payload = mapOf("subject" to "${updated.title}: ${updated.status}"),
            ),
        )
        refreshTasks()
    }

    fun clearError() {
        _state.value = _state.value.copy(lastError = null)
    }

    // ---------- workspace ----------

    /** Selects an already-registered workspace by id. */
    fun selectWorkspace(workspaceId: String) {
        val descriptor: WorkspaceDescriptor = workspaceBroker.descriptorFor(workspaceId)
            ?: run {
                _state.value = _state.value.copy(lastError = "Unknown workspace '$workspaceId'")
                return
            }
        _state.value = _state.value.copy(
            workspaceName = descriptor.displayName,
            lastError = null,
        )
        eventBus.publish(
            EventEnvelope(
                id = "evt-${clock()}-workspace",
                type = "workspace.operation",
                timestampMillis = clock(),
                source = SOURCE,
                payload = mapOf("subject" to "selected ${descriptor.displayName}"),
            ),
        )
        listFiles()
    }

    /**
     * Lists the selected workspace root.
     *
     * The path always goes through [WorkspaceBroker.list], so traversal is
     * refused by the broker rather than by this class.
     */
    fun listFiles(relativePath: String = ".") {
        val workspaceId = currentWorkspaceId ?: return
        _state.value = _state.value.copy(busy = true)
        when (val result = workspaceBroker.list(packageName(), workspaceId, relativePath)) {
            is BrokerResult.Ok -> {
                _state.value = _state.value.copy(
                    files = result.value.map {
                        WorkspaceFileUi(it.name, it.sizeBytes, it.isDirectory)
                    },
                    lastError = null,
                    busy = false,
                )
                eventBus.publish(
                    EventEnvelope(
                        id = "evt-${clock()}-list",
                        type = "workspace.operation",
                        timestampMillis = clock(),
                        source = SOURCE,
                        payload = mapOf("subject" to "listed ${result.value.size} entries"),
                    ),
                )
            }
            is BrokerResult.PathRefused -> {
                _state.value = _state.value.copy(busy = false, files = emptyList())
                recordRefusal("list", result.resolution.reason)
            }
            is BrokerResult.Denied -> {
                _state.value = _state.value.copy(busy = false, files = emptyList())
                recordRefusal("list", result.decision.reason)
            }
            is BrokerResult.BackendFailed ->
                _state.value = _state.value.copy(
                    busy = false,
                    lastError = result.reason,
                )
        }
    }

    /** Refusals are surfaced as security violations in the feed, not hidden. */
    private fun recordRefusal(operation: String, reason: String) {
        _state.value = _state.value.copy(lastError = reason)
        eventBus.publish(
            EventEnvelope(
                id = "evt-${clock()}-violation",
                type = "security.violation",
                timestampMillis = clock(),
                source = SOURCE,
                payload = mapOf(
                    "subject" to "workspace.$operation refused",
                    "detail" to reason,
                ),
            ),
        )
    }

    private var currentWorkspaceId: String? = null

    fun selectWorkspaceId(id: String) {
        currentWorkspaceId = id
        selectWorkspace(id)
    }

    private fun packageName(): String = PACKAGE

    companion object {
        const val SOURCE = "ui"
        const val PACKAGE = "dev.vitngan.harness"
        const val MAX_ACTIVITY = 200
    }
}
