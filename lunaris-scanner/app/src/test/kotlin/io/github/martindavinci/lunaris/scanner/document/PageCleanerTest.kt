package io.github.martindavinci.lunaris.scanner.document

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageCleanerTest {
    private val size = 400

    private fun rgb(r: Int, g: Int, b: Int) = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    private fun Int.r() = this shr 16 and 0xFF
    private fun Int.g() = this shr 8 and 0xFF
    private fun Int.b() = this and 0xFF

    private fun inInk(x: Int, y: Int) = x in 183 until 187 && y in 100 until 300

    /** Paper that a shadow darkens from right to left, with a stroke of ink in the middle. */
    private fun shadowedPage(): PagePixels = PagePixels(
        IntArray(size * size) { index ->
            val x = index % size
            val y = index / size
            val light = 120 + 100 * x / size
            val value = if (inInk(x, y)) light * 3 / 10 else light
            rgb(value, value, value)
        },
        size,
        size,
    )

    private fun PagePixels.at(x: Int, y: Int) = pixels[y * width + x]

    @Test fun aShadow_leavesThePaperWhite() {
        val page = shadowedPage()

        cleanPage(page, CleanMode.COLOUR)

        assertThat(page.at(20, 20).r()).isAtLeast(245)
        assertThat(page.at(380, 380).r()).isAtLeast(245)
    }

    @Test fun theInk_comesOutDarkerThanItWent() {
        val page = shadowedPage()
        val before = page.at(185, 200).r()

        cleanPage(page, CleanMode.COLOUR)

        assertThat(page.at(185, 200).r()).isLessThan(before)
    }

    @Test fun aLampsColour_goesWithThePaper() {
        val page = PagePixels(IntArray(size * size) { rgb(230, 205, 150) }, size, size)

        cleanPage(page, CleanMode.COLOUR)

        assertThat(page.at(200, 200).r()).isEqualTo(255)
        assertThat(page.at(200, 200).g()).isEqualTo(255)
        assertThat(page.at(200, 200).b()).isEqualTo(255)
    }

    @Test fun colourInk_staysColoured() {
        val page = PagePixels(
            IntArray(size * size) { index -> if (inInk(index % size, index / size)) rgb(200, 40, 40) else rgb(240, 240, 240) },
            size,
            size,
        )

        cleanPage(page, CleanMode.COLOUR)

        val ink = page.at(185, 200)
        assertThat(ink.r()).isGreaterThan(ink.g() + 50)
    }

    @Test fun greyscale_hasNoColourLeft() {
        val page = PagePixels(IntArray(size * size) { rgb(200, 90 + it % 100, 40) }, size, size)

        cleanPage(page, CleanMode.GREY)

        assertThat(page.pixels.all { it.r() == it.g() && it.g() == it.b() }).isTrue()
    }

    @Test fun blackAndWhite_hasOnlyTwoTones_withTheInkBlack() {
        val page = shadowedPage()

        cleanPage(page, CleanMode.BLACK_AND_WHITE)

        assertThat(page.pixels.toSet()).containsExactly(rgb(0, 0, 0), rgb(255, 255, 255))
        assertThat(page.at(185, 200)).isEqualTo(rgb(0, 0, 0))
        assertThat(page.at(20, 20)).isEqualTo(rgb(255, 255, 255))
    }

    @Test fun anEmptyPage_staysWhiteInBlackAndWhite() {
        val page = PagePixels(IntArray(size * size) { rgb(180, 180, 180) }, size, size)

        cleanPage(page, CleanMode.BLACK_AND_WHITE)

        assertThat(page.pixels.toSet()).containsExactly(rgb(255, 255, 255))
    }

    @Test fun straightenOnly_touchesNothing() {
        val page = shadowedPage()
        val before = page.pixels.toList()

        cleanPage(page, CleanMode.NONE)

        assertThat(page.pixels.toList()).isEqualTo(before)
    }

    @Test fun aBlackPage_doesNotBreak() {
        val page = PagePixels(IntArray(size * size) { rgb(0, 0, 0) }, size, size)

        cleanPage(page, CleanMode.COLOUR)

        assertThat(page.pixels.toSet()).containsExactly(rgb(0, 0, 0))
    }

    @Test fun aVerySmallPage_doesNotBreak() {
        val page = PagePixels(intArrayOf(rgb(200, 200, 200)), 1, 1)

        cleanPage(page, CleanMode.COLOUR)

        assertThat(page.pixels[0]).isEqualTo(rgb(255, 255, 255))
    }

    @Test fun otsu_splitsBetweenTheTwoTones() {
        val histogram = IntArray(256)
        histogram[30] = 100
        histogram[200] = 900

        assertThat(otsuThreshold(histogram, 1000)).isIn(31..200)
    }

    @Test fun otsu_withOneTone_stopsInTheMiddle() {
        val histogram = IntArray(256)
        histogram[255] = 1000

        assertThat(otsuThreshold(histogram, 1000)).isEqualTo(128)
    }
}
