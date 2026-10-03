package dev.vitngan.harness.ui

import dev.vitngan.harness.core.event.EventBusImpl
import dev.vitngan.harness.core.policy.AppPolicy
import dev.vitngan.harness.core.policy.Capability
import dev.vitngan.harness.core.policy.CapabilityManager
import dev.vitngan.harness.core.policy.TrustedPolicyEngine
import dev.vitngan.harness.core.task.TaskManagerImpl
import dev.vitngan.harness.core.task.TaskStatus
import dev.vitngan.harness.core.workspace.AppPrivateBackend
import dev.vitngan.harness.core.workspace.SecurityPathResolver
import dev.vitngan.harness.core.workspace.WorkspaceBackendType
import dev.vitngan.harness.core.workspace.WorkspaceBroker
import dev.vitngan.harness.core.workspace.WorkspaceDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * ViewModel behaviour.
 *
 * These tests also prove the security boundary from the UI side: the view
 * model can only reach the workspace through the broker, so a traversal
 * attempt made through the UI is refused exactly as it would be anywhere
 * else.
 */
class TaskViewModelTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val pkg = "dev.vitngan.harness"
    private lateinit var root: File
    private lateinit var vm: TaskViewModel
    private var now = 0L

    @Before
    fun setUp() {
        root = temp.newFolder("ws")
        File(root, "note.txt").writeText("hello")
        File(root, "sub").mkdirs()

        val policy = AppPolicy(pkg, Capability.entries.toSet())
        val broker = WorkspaceBroker(
            CapabilityManager(TrustedPolicyEngine({ policy })),
            SecurityPathResolver(),
        )
        broker.register(
            AppPrivateBackend(
                WorkspaceDescriptor(
                    id = "main",
                    displayName = "Main workspace",
                    backendType = WorkspaceBackendType.APP_PRIVATE,
                    rootPath = root.canonicalPath,
                ),
            ),
        )

        vm = TaskViewModel(
            taskManager = TaskManagerImpl(),
            workspaceBroker = broker,
            eventBus = EventBusImpl(),
            clock = { ++now },
        )
        vm.observe()
    }

    // ---------- tasks ----------

    @Test
    fun `creating a task adds it to state`() {
        vm.createTask("write tests")
        assertEquals(1, vm.state.value.tasks.size)
        assertEquals("write tests", vm.state.value.tasks.first().title)
        assertEquals(TaskStatus.PENDING, vm.state.value.tasks.first().status)
    }

    @Test
    fun `blank task title is rejected with an error`() {
        vm.createTask("   ")
        assertEquals(0, vm.state.value.tasks.size)
        assertNotNull(vm.state.value.lastError)
    }

    @Test
    fun `task transition updates state and emits an event`() {
        vm.createTask("x")
        val id = vm.state.value.tasks.first().id
        vm.transitionTask(id, TaskStatus.RUNNING)

        assertEquals(TaskStatus.RUNNING, vm.state.value.tasks.first().status)
        assertTrue(vm.state.value.activity.any { it.type == "task.transition" })
    }

    @Test
    fun `illegal transition surfaces an error and does not change state`() {
        vm.createTask("x")
        val id = vm.state.value.tasks.first().id
        vm.transitionTask(id, TaskStatus.COMPLETED)

        assertEquals(TaskStatus.PENDING, vm.state.value.tasks.first().status)
        assertNotNull(vm.state.value.lastError)
    }

    // ---------- workspace ----------

    @Test
    fun `selecting a workspace sets its name and lists files`() {
        vm.selectWorkspaceId("main")
        val state = vm.state.value
        assertTrue(state.hasWorkspace)
        assertEquals("Main workspace", state.workspaceName)
        assertTrue(state.files.any { it.name == "note.txt" })
    }

    @Test
    fun `selecting an unknown workspace surfaces an error`() {
        vm.selectWorkspaceId("nope")
        assertFalse(vm.state.value.hasWorkspace)
        assertNotNull(vm.state.value.lastError)
    }

    @Test
    fun `traversal through the view model is refused and recorded as a violation`() {
        vm.selectWorkspaceId("main")
        val before = vm.state.value.violationCount
        vm.listFiles("../../etc")

        assertTrue("traversal must be refused", vm.state.value.violationCount > before)
        assertNotNull(vm.state.value.lastError)
        assertTrue(vm.state.value.files.isEmpty())
    }

    @Test
    fun `absolute path through the view model is refused`() {
        vm.selectWorkspaceId("main")
        vm.listFiles("/etc/passwd")
        assertNotNull(vm.state.value.lastError)
        assertTrue(vm.state.value.violationCount > 0)
    }

    // ---------- activity ----------

    @Test
    fun `activity feed records task creation`() {
        vm.createTask("observed task")
        assertTrue(vm.state.value.activity.any { it.type == "task.created" })
    }

    @Test
    fun `activity feed is bounded`() {
        repeat(TaskViewModel.MAX_ACTIVITY + 20) { vm.createTask("task $it") }
        assertTrue(
            "feed must be bounded",
            vm.state.value.activity.size <= TaskViewModel.MAX_ACTIVITY,
        )
    }

    @Test
    fun `activity rows carry only summary data`() {
        vm.createTask("x")
        val row = vm.state.value.activity.first()
        // ExecutionSummary has no chain-of-thought field, so nothing can leak.
        assertNotNull(row.summary.objective)
        assertTrue(row.label.isNotBlank())
    }

    @Test
    fun `stop observing detaches the subscription`() {
        vm.stopObserving()
        vm.createTask("after stop")
        assertTrue(
            "no activity should arrive after unsubscribing",
            vm.state.value.activity.none { it.type == "task.created" },
        )
    }

    @Test
    fun `clear error resets the message`() {
        vm.createTask("   ")
        assertNotNull(vm.state.value.lastError)
        vm.clearError()
        assertNull(vm.state.value.lastError)
    }
}