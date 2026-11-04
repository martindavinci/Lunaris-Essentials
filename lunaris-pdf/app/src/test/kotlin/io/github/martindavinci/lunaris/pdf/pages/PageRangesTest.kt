package io.github.martindavinci.lunaris.pdf.pages

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageRangesTest {
    @Test
    fun `ranges, single pages and an open end`() {
        assertThat(PageRanges.parse("1-3, 5, 7-", 9)).containsExactly(0..2, 4..4, 6..8).inOrder()
    }

    @Test
    fun `spaces do not matter, and the long dash counts`() {
        assertThat(PageRanges.parse("  2 - 4 ,6 ", 9)).containsExactly(1..3, 5..5).inOrder()
        assertThat(PageRanges.parse("1–7", 9)).containsExactly(0..6)
    }

    @Test
    fun `an open start begins at page one`() {
        assertThat(PageRanges.parse("-3", 9)).containsExactly(0..2)
    }

    @Test
    fun `what cannot be pages is null`() {
        listOf("", " ", "abc", "0", "10", "3-1", "1-10", "1,,2", "1-2-3", "-", "1,", "99999999999").forEach {
            assertThat(PageRanges.parse(it, 9)).isNull()
        }
    }

    @Test
    fun `the count of pages counts repeats`() {
        assertThat(PageRanges.pageCount(listOf(0..2, 2..2))).isEqualTo(4)
    }
}
