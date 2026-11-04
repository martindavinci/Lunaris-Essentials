package io.github.martindavinci.lunaris.pdf.pages

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SplitPlanTest {
    private val slots = List(9) { PageSlot(it.toLong(), SlotContent.PageOf(0, it)) }

    @Test
    fun `each row becomes a file, with the pages as they stand now`() {
        val reordered = slots.reversed()
        val outputs = SplitPlan(listOf("1-2", "9")).outputs(reordered)!!
        assertThat(outputs.map { list -> list.map { it.id } }).containsExactly(listOf(8L, 7L), listOf(0L)).inOrder()
    }

    @Test
    fun `one row may hold several parts`() {
        assertThat(SplitPlan(listOf("1-2, 5")).outputs(slots)!!.single().map { it.id }).containsExactly(0L, 1L, 4L).inOrder()
    }

    @Test
    fun `a bad row or no row means no files`() {
        assertThat(SplitPlan(listOf("1-2", "12")).outputs(slots)).isNull()
        assertThat(SplitPlan(listOf("1-2", "")).outputs(slots)).isNull()
        assertThat(SplitPlan(emptyList()).outputs(slots)).isNull()
    }

    @Test
    fun `every page on its own`() {
        val outputs = SplitPlan(emptyList(), everyPage = true).outputs(slots)!!
        assertThat(outputs).hasSize(9)
        assertThat(outputs[3].single().id).isEqualTo(3L)
    }

    @Test
    fun `a row tells its count, or null while it is wrong`() {
        val plan = SplitPlan(listOf("1-7", "x", ""))
        assertThat(plan.countOf(0, 9)).isEqualTo(7)
        assertThat(plan.countOf(1, 9)).isNull()
        assertThat(plan.countOf(2, 9)).isNull()
    }

    @Test
    fun `how many files it makes`() {
        assertThat(SplitPlan(listOf("1", "2")).fileCount(9)).isEqualTo(2)
        assertThat(SplitPlan(emptyList(), everyPage = true).fileCount(9)).isEqualTo(9)
    }

    @Test
    fun `file names carry the pages, with the base of the name`() {
        val rows = SplitPlan(listOf("1-7", " 8 - 13 , 15")).fileNames("Moon.pdf", 20)
        assertThat(rows).containsExactly("Moon (1-7).pdf", "Moon (8-13, 15).pdf").inOrder()
        assertThat(SplitPlan(emptyList(), everyPage = true).fileNames("Moon", 2)).containsExactly("Moon (1).pdf", "Moon (2).pdf").inOrder()
    }
}
