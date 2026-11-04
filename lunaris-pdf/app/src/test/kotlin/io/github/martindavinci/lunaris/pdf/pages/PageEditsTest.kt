package io.github.martindavinci.lunaris.pdf.pages

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageEditsTest {
    private fun edits(count: Int = 6) = PageEdits(List(count) { PageSlot(it.toLong(), SlotContent.PageOf(0, it)) })

    private fun PageEdits.ids() = slots.map { it.id }

    @Test
    fun `rotating wraps at a full turn`() {
        val edits = edits()
        repeat(4) { edits.rotate(setOf(1L)) }
        assertThat(edits.slots[1].rotation).isEqualTo(0)
        edits.rotate(setOf(1L))
        assertThat(edits.slots[1].rotation).isEqualTo(90)
        assertThat(edits.slots[0].rotation).isEqualTo(0)
    }

    @Test
    fun `deleting every page says no and changes nothing`() {
        val edits = edits(2)
        assertThat(edits.delete(setOf(0L, 1L))).isFalse()
        assertThat(edits.ids()).containsExactly(0L, 1L).inOrder()
        assertThat(edits.changes).isEqualTo(0)
    }

    @Test
    fun `deleting takes the pages out`() {
        val edits = edits()
        assertThat(edits.delete(setOf(0L, 3L))).isTrue()
        assertThat(edits.ids()).containsExactly(1L, 2L, 4L, 5L).inOrder()
    }

    @Test
    fun `a scattered group moves together, in its order, to the place shown`() {
        val edits = edits()
        edits.move(setOf(4L, 1L), 3)
        assertThat(edits.ids()).containsExactly(0L, 2L, 1L, 4L, 3L, 5L).inOrder()
    }

    @Test
    fun `a move to the end and to the start lands there`() {
        val edits = edits(4)
        edits.move(setOf(0L), 4)
        assertThat(edits.ids()).containsExactly(1L, 2L, 3L, 0L).inOrder()
        edits.move(setOf(3L, 0L), 0)
        assertThat(edits.ids()).containsExactly(3L, 0L, 1L, 2L).inOrder()
    }

    @Test
    fun `a move that changes nothing is not a change`() {
        val edits = edits(4)
        edits.move(setOf(1L), 1)
        edits.move(setOf(1L), 2)
        assertThat(edits.changes).isEqualTo(0)
        assertThat(edits.canUndo).isFalse()
    }

    @Test
    fun `undo and redo cross every kind of change`() {
        val edits = edits(3)
        edits.move(setOf(0L), 3)
        edits.rotate(setOf(1L))
        edits.delete(setOf(2L))
        edits.undo()
        edits.undo()
        assertThat(edits.ids()).containsExactly(1L, 2L, 0L).inOrder()
        assertThat(edits.slots[0].rotation).isEqualTo(0)
        edits.redo()
        assertThat(edits.slots[0].rotation).isEqualTo(90)
        assertThat(edits.canRedo).isTrue()
        edits.redo()
        assertThat(edits.ids()).containsExactly(1L, 0L).inOrder()
        assertThat(edits.canRedo).isFalse()
    }

    @Test
    fun `a new change clears what could be redone`() {
        val edits = edits(3)
        edits.rotate(setOf(0L))
        edits.undo()
        edits.rotate(setOf(1L))
        assertThat(edits.canRedo).isFalse()
    }

    @Test
    fun `changes count from the last save, either way`() {
        val edits = edits(3)
        edits.rotate(setOf(0L))
        edits.rotate(setOf(1L))
        assertThat(edits.changes).isEqualTo(2)
        edits.markSaved()
        assertThat(edits.changes).isEqualTo(0)
        assertThat(edits.canUndo).isTrue()
        edits.undo()
        assertThat(edits.changes).isEqualTo(1)
    }

    @Test
    fun `the oldest step goes past the limit, and the count stays right`() {
        val edits = edits(2)
        repeat(PageEdits.MAX_UNDO + 10) { edits.rotate(setOf(0L)) }
        assertThat(edits.changes).isEqualTo(PageEdits.MAX_UNDO + 10)
        repeat(PageEdits.MAX_UNDO) { edits.undo() }
        assertThat(edits.canUndo).isFalse()
        assertThat(edits.changes).isEqualTo(10)
    }

    @Test
    fun `new slots get ids of their own, and land at the index`() {
        val edits = edits(2)
        val added = edits.insert(1, listOf(SlotContent.Blank(100f, 200f), SlotContent.PictureOf(1, 90)))
        assertThat(edits.slots.map { it.id }).containsExactly(0L, added[0], added[1], 1L).inOrder()
        assertThat(added.toSet()).hasSize(2)
        assertThat(added).containsNoneOf(0L, 1L)
        assertThat(edits.slots[2].rotation).isEqualTo(90)
    }

    @Test
    fun `an edit survives being written down`() {
        val edits = edits(3)
        edits.insert(1, listOf(SlotContent.Blank(595.5f, 842f), SlotContent.PictureOf(1, 270)))
        edits.rotate(setOf(0L))
        edits.markSaved()
        edits.delete(setOf(2L))
        val back = PageEdits.decode(edits.encode())
        assertThat(back).isNotNull()
        assertThat(back!!.slots).isEqualTo(edits.slots)
        assertThat(back.changes).isEqualTo(1)
        val added = back.insert(0, listOf(SlotContent.Blank(1f, 1f)))
        assertThat(edits.slots.map { it.id }).doesNotContain(added[0])
    }

    @Test
    fun `damaged text is no edit`() {
        listOf("", "x", "1|2", "a|b|c", "0|0|0,z,0,0,0", "0|0|0,p,0,0").forEach {
            assertThat(PageEdits.decode(it)).isNull()
        }
    }
}
