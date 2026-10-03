package dev.vitngan.harness.core.persistence

import dev.vitngan.harness.core.task.TaskManagerImpl
import dev.vitngan.harness.core.task.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task persistence across a serialise/deserialise round trip. */
class TaskPersistenceTest {

    private var seq = 0

    private fun manager(store: TaskStore) =
        TaskManagerImpl(store, { 1_000L }, { "t${++seq}" })

    @Test
    fun `task survives a text round trip`() {
        val store = TaskStore.TextBacked("", TaskCodec)
        val m = manager(store)
        val task = m.create("write tests", "desc", "ws-1")
        m.transition(task.id, TaskStatus.RUNNING)

        val serialised = store.serialised()

        // Fresh manager over the same persisted text.
        val reloaded = TaskStore.TextBacked(serialised, TaskCodec)
        val m2 = manager(reloaded)

        val restored = m2.get(task.id)
        assertNotNull(restored)
        assertEquals("write tests", restored!!.title)
        assertEquals(TaskStatus.RUNNING, restored.status)
        assertEquals("ws-1", restored.workspaceId)
    }

    @Test
    fun `an illegal transition is still refused through a persisted store`() {
        val store = TaskStore.TextBacked("", TaskCodec)
        val m = manager(store)
        val task = m.create("x")

        assertNull(m.transition(task.id, TaskStatus.COMPLETED))
        assertEquals(TaskStatus.PENDING, m.get(task.id)!!.status)
    }

    @Test
    fun `persisted status survives and cannot be resurrected`() {
        val store = TaskStore.TextBacked("", TaskCodec)
        val m = manager(store)
        val task = m.create("x")
        m.transition(task.id, TaskStatus.RUNNING)
        m.transition(task.id, TaskStatus.COMPLETED)

        val reloaded = TaskStore.TextBacked(store.serialised(), TaskCodec)
        val m2 = manager(reloaded)

        assertEquals(TaskStatus.COMPLETED, m2.get(task.id)!!.status)
        assertNull(m2.transition(task.id, TaskStatus.RUNNING))
    }

    @Test
    fun `failure reason is persisted`() {
        val store = TaskStore.TextBacked("", TaskCodec)
        val m = manager(store)
        val task = m.create("x")
        m.transition(task.id, TaskStatus.RUNNING)
        m.transition(task.id, TaskStatus.FAILED, "device disconnected")

        val m2 = manager(TaskStore.TextBacked(store.serialised(), TaskCodec))
        assertEquals("device disconnected", m2.get(task.id)!!.failureReason)
    }

    @Test
    fun `delete removes from the persisted text`() {
        val store = TaskStore.TextBacked("", TaskCodec)
        val m = manager(store)
        val task = m.create("x")
        assertTrue(m.delete(task.id))
        assertNull(TaskStore.TextBacked(store.serialised(), TaskCodec).get(task.id))
    }

    @Test
    fun `corrupt persisted text decodes to empty rather than throwing`() {
        val store = TaskStore.TextBacked("{ this is not valid json", TaskCodec)
        assertEquals(emptyList<TaskRecord>(), TaskCodec.fromText("{ this is not valid json"))
        assertEquals(0, manager(store).count())
    }

    @Test
    fun `unknown status in storage is dropped, not trusted`() {
        val bogus = """[{"id":"t1","title":"x","status":"SUPER_ADMIN"}]"""
        assertTrue(TaskCodec.fromText(bogus).isEmpty())
    }

    @Test
    fun `blank id in storage is dropped`() {
        val bogus = """[{"id":"","title":"x","status":"PENDING"}]"""
        assertTrue(TaskCodec.fromText(bogus).isEmpty())
    }

    @Test
    fun `empty text decodes to empty list`() {
        assertTrue(TaskCodec.fromText("").isEmpty())
        assertTrue(TaskCodec.fromText("   ").isEmpty())
    }
}