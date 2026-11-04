package io.github.martindavinci.lunaris.pdf.feature.pages

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GridDropTest {
    /** Three columns 100 wide, rows 150 tall, from page [first]. */
    private fun cells(count: Int, first: Int = 0) = List(count) { i ->
        val column = i % 3
        val row = i / 3
        GridCell(first + i, column * 100f, row * 150f, column * 100f + 100f, row * 150f + 150f)
    }

    @Test
    fun `left of the middle is before the page, right of it after`() {
        assertThat(GridDrop.placeAt(cells(9), 20f, 200f)).isEqualTo(3)
        assertThat(GridDrop.placeAt(cells(9), 80f, 200f)).isEqualTo(4)
        assertThat(GridDrop.placeAt(cells(9), 180f, 200f)).isEqualTo(5)
    }

    @Test
    fun `past the end of a row is before the first of the next`() {
        assertThat(GridDrop.placeAt(cells(9), 290f, 200f)).isEqualTo(6)
    }

    @Test
    fun `a last row that is not full ends where it ends`() {
        assertThat(GridDrop.placeAt(cells(7), 250f, 400f)).isEqualTo(7)
    }

    @Test
    fun `above and below every page go to the nearest row`() {
        assertThat(GridDrop.placeAt(cells(9), 20f, -50f)).isEqualTo(0)
        assertThat(GridDrop.placeAt(cells(9), 290f, 900f)).isEqualTo(9)
    }

    @Test
    fun `a scrolled grid counts from its first page on screen`() {
        assertThat(GridDrop.placeAt(cells(3, first = 30), 20f, 50f)).isEqualTo(30)
    }

    @Test
    fun `no pages on screen, no place`() {
        assertThat(GridDrop.placeAt(emptyList(), 0f, 0f)).isNull()
    }
}
