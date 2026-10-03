package dev.vitngan.harness.core.diff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffEngineTest {

    private val engine = DiffEngine()

    @Test
    fun `identical text produces no hunks`() {
        val r = engine.diffText("a\nb\nc", "a\nb\nc")
        assertTrue(r.isEmpty)
        assertEquals(0, r.addedLines)
        assertEquals(0, r.removedLines)
    }

    @Test
    fun `pure addition is detected`() {
        val r = engine.diffText("a\nb", "a\nNEW\nb")
        assertEquals(1, r.addedLines)
        assertEquals(0, r.removedLines)
        assertTrue(r.render().contains("+NEW"))
    }

    @Test
    fun `pure removal is detected`() {
        val r = engine.diffText("a\nGONE\nb", "a\nb")
        assertEquals(0, r.addedLines)
        assertEquals(1, r.removedLines)
        assertTrue(r.render().contains("-GONE"))
    }

    @Test
    fun `replacement counts as one removal and one addition`() {
        val r = engine.diffText("a\nOLD\nb", "a\nNEW\nb")
        assertEquals(1, r.addedLines)
        assertEquals(1, r.removedLines)
    }

    @Test
    fun `empty original against content is all additions`() {
        val r = engine.diffText("", "x\ny")
        assertEquals(2, r.addedLines)
        assertEquals(0, r.removedLines)
    }

    @Test
    fun `content against empty original is all removals`() {
        val r = engine.diffText("x\ny", "")
        assertEquals(0, r.addedLines)
        assertEquals(2, r.removedLines)
    }

    @Test
    fun `hunk position points at the changed line`() {
        // "a" is unchanged, so the change to line 2 must report position 2.
        val r = engine.diffText("a\nOLD", "a\nNEW")
        assertTrue(r.hunks.isNotEmpty())
        assertEquals(2, r.hunks.first().originalStart)
        assertEquals(2, r.hunks.first().revisedStart)
    }

    @Test
    fun `hunk position is one when the first line changes`() {
        val r = engine.diffText("OLD\na", "NEW\na")
        assertEquals(1, r.hunks.first().originalStart)
        assertEquals(1, r.hunks.first().revisedStart)
    }

    @Test
    fun `diff is deterministic across repeated calls`() {
        val a = "one\ntwo\nthree\nfour"
        val b = "one\ntwo\nCHANGED\nfour\nfive"
        val first = engine.diffText(a, b).render()
        repeat(5) { assertEquals(first, engine.diffText(a, b).render()) }
    }

    @Test
    fun `diff accepts line lists directly`() {
        val r = engine.diff(listOf("a"), listOf("a", "b"))
        assertEquals(1, r.addedLines)
    }
}