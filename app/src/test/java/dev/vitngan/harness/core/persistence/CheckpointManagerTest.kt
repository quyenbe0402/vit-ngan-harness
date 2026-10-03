package dev.vitngan.harness.core.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checkpoint save, restore and isolation. */
class CheckpointManagerTest {

    private var now = 1_000L
    private var seq = 0
    private val manager = CheckpointManager(
        store = CheckpointStore.InMemory(),
        clock = { now++ },
        idGenerator = { "ck-${++seq}" },
    )

    @Test
    fun `saved checkpoint is retrievable`() {
        val c = manager.save("t1", "before edit", "state-1")
        assertNotNull(manager.get(c.id))
        assertEquals("state-1", manager.get(c.id)!!.payload)
    }

    @Test
    fun `restore returns the checkpoint for the owning task`() {
        val c = manager.save("t1", "cp", "payload")
        val restored = manager.restore(c.id, "t1")
        assertNotNull(restored)
        assertEquals("payload", restored!!.payload)
    }

    @Test
    fun `restore refuses a checkpoint owned by another task`() {
        val c = manager.save("t1", "cp", "payload")
        assertNull("cross-task restore must be refused", manager.restore(c.id, "t2"))
    }

    @Test
    fun `restore of an unknown id returns null`() {
        assertNull(manager.restore("nope", "t1"))
    }

    @Test
    fun `checkpoints are scoped per task`() {
        manager.save("t1", "a", "x")
        manager.save("t2", "b", "y")

        assertEquals(1, manager.forTask("t1").size)
        assertEquals(1, manager.forTask("t2").size)
        assertEquals("a", manager.forTask("t1").first().label)
    }

    @Test
    fun `latest for task is the newest by timestamp`() {
        manager.save("t1", "first", "1")
        manager.save("t1", "second", "2")
        assertEquals("second", manager.latestForTask("t1")!!.label)
    }

    @Test
    fun `lineage walks parents back to the root`() {
        val a = manager.save("t1", "a", "1")
        val b = manager.save("t1", "b", "2", parentId = a.id)
        val c = manager.save("t1", "c", "3", parentId = b.id)

        assertEquals(listOf("c", "b", "a"), manager.lineage(c.id).map { it.label })
    }

    @Test
    fun `lineage is cycle safe`() {
        val a = manager.save("t1", "a", "1")
        val b = manager.save("t1", "b", "2", parentId = a.id)
        // Force a cycle: point a's parent at b.
        val store = CheckpointStore.InMemory()
        val m2 = CheckpointManager(store, { 1L }, { "x" })
        val x = Checkpoint("x", "t", "x", 1L, parentId = "y")
        val y = Checkpoint("y", "t", "y", 2L, parentId = "x")
        store.put(x); store.put(y)

        val lineage = m2.lineage("x")
        assertEquals("a cycle must terminate", 2, lineage.size)
        assertNotNull(b)
    }

    @Test
    fun `delete removes a checkpoint`() {
        val c = manager.save("t1", "cp", "x")
        assertTrue(manager.delete(c.id))
        assertNull(manager.get(c.id))
        assertFalse(manager.delete(c.id))
    }

    @Test
    fun `blank task id is rejected`() {
        var threw = false
        try {
            manager.save("", "label", "payload")
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `blank label is rejected`() {
        var threw = false
        try {
            manager.save("t1", "  ", "payload")
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `count reflects stored checkpoints`() {
        manager.save("t1", "a", "1")
        manager.save("t2", "b", "2")
        assertEquals(2, manager.count())
        assertEquals(1, manager.count("t1"))
    }
}