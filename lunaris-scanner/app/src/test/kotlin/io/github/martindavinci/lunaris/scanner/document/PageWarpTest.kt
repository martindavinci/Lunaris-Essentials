package io.github.martindavinci.lunaris.scanner.document

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageWarpTest {
    private val red = 0xFFFF0000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    private fun corners(left: Float, top: Float, right: Float, bottom: Float) = PageCorners(
        PagePoint(left, top),
        PagePoint(right, top),
        PagePoint(right, bottom),
        PagePoint(left, bottom),
    )

    @Test fun theMapping_carriesEachCornerOfTheRectangleOntoTheQuad() {
        val quad = PageCorners(PagePoint(10f, 20f), PagePoint(90f, 5f), PagePoint(120f, 150f), PagePoint(3f, 110f))
        val mapping = PerspectiveMapping.between(50, 80, quad)

        listOf(0.0 to 0.0, 50.0 to 0.0, 50.0 to 80.0, 0.0 to 80.0).forEachIndexed { index, (u, v) ->
            assertThat(mapping.x(u, v)).isWithin(1e-6).of(quad.points[index].x.toDouble())
            assertThat(mapping.y(u, v)).isWithin(1e-6).of(quad.points[index].y.toDouble())
        }
    }

    @Test fun theWholePhotoAtItsOwnSize_comesBackUnchanged() {
        val photo = PagePixels(IntArray(12) { 0xFF000000.toInt() or (it * 20 shl 16) or (it * 10) }, 4, 3)

        val page = warp(photo, corners(0f, 0f, 4f, 3f), 4, 3)

        assertThat(page.pixels.toList()).isEqualTo(photo.pixels.toList())
    }

    @Test fun onlyWhatTheCornersEncloseIsKept() {
        val photo = PagePixels(
            IntArray(100 * 100) { index ->
                val x = index % 100
                val y = index / 100
                if (x in 20 until 60 && y in 30 until 80) red else white
            },
            100,
            100,
        )

        val page = warp(photo, corners(22f, 32f, 58f, 78f), 30, 40)

        assertThat(page.pixels.toSet()).containsExactly(red)
    }

    @Test fun aTiltedPage_comesOutUpright() {
        val quad = PageCorners(PagePoint(30f, 10f), PagePoint(70f, 20f), PagePoint(90f, 90f), PagePoint(10f, 80f))
        val photo = PagePixels(IntArray(100 * 100) { if (inside(quad, it % 100 + 0.5f, it / 100 + 0.5f)) red else white }, 100, 100)
        val centre = PagePoint(50f, 50f)
        val insetPoints = quad.points.map { PagePoint(centre.x + (it.x - centre.x) * 0.9f, centre.y + (it.y - centre.y) * 0.9f) }
        val inset = PageCorners(insetPoints[0], insetPoints[1], insetPoints[2], insetPoints[3])

        val page = warp(photo, inset, 20, 30)

        assertThat(page.pixels.toSet()).containsExactly(red)
    }

    @Test fun everyPixelIsOpaque() {
        val photo = PagePixels(IntArray(4) { 0x00123456 }, 2, 2)

        val page = warp(photo, corners(0f, 0f, 2f, 2f), 2, 2)

        assertThat(page.pixels.all { it ushr 24 == 0xFF }).isTrue()
    }

    private fun inside(quad: PageCorners, x: Float, y: Float): Boolean = quad.points.indices.all { index ->
        val a = quad.points[index]
        val b = quad.points[(index + 1) % quad.points.size]
        (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x) >= 0
    }
}
