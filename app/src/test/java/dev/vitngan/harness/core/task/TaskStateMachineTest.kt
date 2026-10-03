package dev.vitngan.harness.core.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskStateMachineTest {

    private var now = 0L
    private val manager = TaskManagerImpl(clock = { now }, idGenerator = { "t1" })

    @Test
    fun `new task starts pending`() {
        val task = manager.create("do work")
        assertEquals(TaskStatus.PENDING, task.status)
        assertEquals("t1", task.id)
    }

    @Test
    fun `pending to running is legal`() {
        manager.create("x")
        assertNotNull(manager.transition("t1", TaskStatus.RUNNING))
        assertEquals(TaskStatus.RUNNING, manager.get("t1")!!.status)
    }

    @Test
    fun `pending directly to completed is illegal`() {
        manager.create("x")
        assertNull(manager.transition("t1", TaskStatus.COMPLETED))
        assertEquals(TaskStatus.PENDING, manager.get("t1")!!.status)
    }

    @Test
    fun `running to completed is legal`() {
        manager.create("x")
        manager.transition("t1", TaskStatus.RUNNING)
        assertNotNull(manager.transition("t1", TaskStatus.COMPLETED))
        assertTrue(manager.get("t1")!!.isTerminal)
    }

    @Test
    fun `running to paused and back is legal`() {
        manager.create("x")
        manager.transition("t1", TaskStatus.RUNNING)
        assertNotNull(manager.transition("t1", TaskStatus.PAUSED))
        assertNotNull(manager.transition("t1", TaskStatus.RUNNING))
    }

    @Test
    fun `terminal states cannot be resurrected`() {
        manager.create("x")
        manager.transition("t1", TaskStatus.RUNNING)
        manager.transition("t1", TaskStatus.COMPLETED)

        assertNull(manager.transition("t1", TaskStatus.RUNNING))
        assertNull(manager.transition("t1", TaskStatus.PENDING))
        assertEquals(TaskStatus.COMPLETED, manager.get("t1")!!.status)
    }

    @Test
    fun `failed records a reason and is terminal`() {
        manager.create("x")
        manager.transition("t1", TaskStatus.RUNNING)
        manager.transition("t1", TaskStatus.FAILED, "boom")
        val task = manager.get("t1")!!
        assertEquals(TaskStatus.FAILED, task.status)
        assertEquals("boom", task.failureReason)
        assertNull(manager.transition("t1", TaskStatus.RUNNING))
    }

    @Test
    fun `cancel works from pending and running`() {
        manager.create("x")
        assertNotNull(manager.cancel("t1"))
        assertEquals(TaskStatus.CANCELLED, manager.get("t1")!!.status)

        val second = TaskManagerImpl(idGenerator = { "t2" })
        second.create("y")
        second.transition("t2", TaskStatus.RUNNING)
        assertNotNull(second.cancel("t2"))
        assertNull(second.cancel("t2"))
    }

    @Test
    fun `transition of unknown task returns null`() {
        assertNull(manager.transition("nope", TaskStatus.RUNNING))
    }

    @Test
    fun `canTransitionTo encodes the machine`() {
        assertTrue(TaskStatus.PENDING.canTransitionTo(TaskStatus.RUNNING))
        assertTrue(!TaskStatus.PENDING.canTransitionTo(TaskStatus.COMPLETED))
        assertTrue(TaskStatus.RUNNING.canTransitionTo(TaskStatus.FAILED))
        assertTrue(!TaskStatus.COMPLETED.canTransitionTo(TaskStatus.RUNNING))
    }
}