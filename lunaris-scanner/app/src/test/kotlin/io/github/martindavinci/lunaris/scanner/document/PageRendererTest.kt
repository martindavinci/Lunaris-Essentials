package io.github.martindavinci.lunaris.scanner.document

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageRendererTest {
    private val a = 0xFF000001.toInt()
    private val b = 0xFF000002.toInt()
    private val c = 0xFF000003.toInt()
    private val d = 0xFF000004.toInt()
    private val e = 0xFF000005.toInt()
    private val f = 0xFF000006.toInt()

    /** a b c, over d e f. */
    private val page = PagePixels(intArrayOf(a, b, c, d, e, f), 3, 2)
    private val whole = PageCorners(PagePoint(0f, 0f), PagePoint(1f, 0f), PagePoint(1f, 1f), PagePoint(0f, 1f))

    @Test fun noTurn_isTheSamePage() {
        assertThat(turned(page, 0)).isSameInstanceAs(page)
    }

    @Test fun aQuarterTurn_goesClockwise() {
        val out = turned(page, 1)

        assertThat(out.width).isEqualTo(2)
        assertThat(out.height).isEqualTo(3)
        assertThat(out.pixels.toList()).containsExactly(d, a, e, b, f, c).inOrder()
    }

    @Test fun aHalfTurn_isUpsideDown() {
        assertThat(turned(page, 2).pixels.toList()).containsExactly(f, e, d, c, b, a).inOrder()
    }

    @Test fun threeQuarterTurns_goAnticlockwise() {
        val out = turned(page, 3)

        assertThat(out.width).isEqualTo(2)
        assertThat(out.pixels.toList()).containsExactly(c, f, b, e, a, d).inOrder()
    }

    @Test fun turnsWrapAround() {
        assertThat(PageSettings().turnedRight().turnedLeft().turn).isEqualTo(0)
        assertThat(PageSettings().turnedLeft().turn).isEqualTo(3)
        assertThat(PageSettings(turn = 3).turnedRight().turn).isEqualTo(0)
    }

    @Test fun render_straightensCleansAndTurns() {
        val photo = PagePixels(IntArray(400 * 200) { 0xFFC8C8C8.toInt() }, 400, 200)

        val out = renderPage(photo, PageSettings(corners = whole, turn = 1), workSize = PageSize(800, 400), longSide = 100)

        assertThat(out.width).isEqualTo(50)
        assertThat(out.height).isEqualTo(100)
        assertThat(out.pixels.toSet()).containsExactly(0xFFFFFFFF.toInt())
    }

    @Test fun render_straightenOnly_keepsThePhotosTones() {
        val photo = PagePixels(IntArray(400 * 200) { 0xFFC8C8C8.toInt() }, 400, 200)
        val settings = PageSettings(corners = whole, clean = CleanMode.NONE)

        val out = renderPage(photo, settings, workSize = PageSize(400, 200), longSide = 100)

        assertThat(out.pixels.toSet()).containsExactly(0xFFC8C8C8.toInt())
    }

    @Test fun render_takesItsProportionsFromTheFullPhoto_notFromAPreview() {
        val preview = PagePixels(IntArray(100 * 50) { 0xFFC8C8C8.toInt() }, 100, 50)
        val settings = PageSettings(corners = whole, clean = CleanMode.NONE)

        val out = renderPage(preview, settings, workSize = PageSize(4000, 2000), longSide = 3000)

        assertThat(out.width).isEqualTo(3000)
        assertThat(out.height).isEqualTo(1500)
    }

    @Test fun shrunk_keepsTheProportions() {
        val out = shrunk(PagePixels(IntArray(1000 * 500) { a }, 1000, 500), 100)

        assertThat(out.width).isEqualTo(100)
        assertThat(out.height).isEqualTo(50)
        assertThat(out.pixels.toSet()).containsExactly(a)
    }

    @Test fun shrunk_leavesASmallerPageAlone() {
        val small = PagePixels(IntArray(10 * 5) { a }, 10, 5)

        assertThat(shrunk(small, 100)).isSameInstanceAs(small)
    }
}
