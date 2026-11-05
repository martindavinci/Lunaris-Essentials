package io.github.martindavinci.lunaris.scanner.document

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs

class PageSurfaceTest {
    private val tilted = PageCorners(PagePoint(10f, 20f), PagePoint(90f, 5f), PagePoint(120f, 150f), PagePoint(3f, 110f))
    private val square = PageCorners(PagePoint(20f, 40f), PagePoint(180f, 40f), PagePoint(180f, 160f), PagePoint(20f, 160f))

    @Test fun withStraightEdges_itIsThePerspectiveAlone() {
        val surface = PageSurface(50, 80, tilted)
        val mapping = PerspectiveMapping.between(50, 80, tilted)

        listOf(0.0 to 0.0, 25.0 to 40.0, 13.0 to 71.0).forEach { (u, v) ->
            assertThat(surface.x(u, v)).isWithin(1e-9).of(mapping.x(u, v))
            assertThat(surface.y(u, v)).isWithin(1e-9).of(mapping.y(u, v))
        }
    }

    @Test fun bendingNeverMovesTheCorners() {
        val bent = tilted.copy(bends = List(4) { PagePoint(7f, -9f) })
        val surface = PageSurface(50, 80, bent)

        listOf(0.0 to 0.0, 50.0 to 0.0, 50.0 to 80.0, 0.0 to 80.0).forEachIndexed { index, (u, v) ->
            assertThat(surface.x(u, v)).isWithin(1e-3).of(tilted.points[index].x.toDouble())
            assertThat(surface.y(u, v)).isWithin(1e-3).of(tilted.points[index].y.toDouble())
        }
    }

    @Test fun theMiddleOfABentEdge_isWhereItsHandleIs() {
        val inShares = PageCorners(PagePoint(0.1f, 0.2f), PagePoint(0.9f, 0.05f), PagePoint(1f, 1f), PagePoint(0.03f, 0.9f))
        val bent = inShares.moved(TOP_HANDLE, PagePoint(0.4f, 0.02f))
        val surface = PageSurface(50, 80, bent)

        assertThat(surface.x(25.0, 0.0)).isWithin(1e-4).of(0.4)
        assertThat(surface.y(25.0, 0.0)).isWithin(1e-4).of(0.02)
    }

    @Test fun theInsideOfThePageFollowsItsEdges_inProportion() {
        val bent = square.copy(bends = listOf(PagePoint(0f, -20f), PagePoint(0f, 0f), PagePoint(0f, 0f), PagePoint(0f, 0f)))
        val surface = PageSurface(100, 100, bent)

        assertThat(surface.y(50.0, 50.0)).isWithin(1e-3).of(100.0 - 10.0)
        assertThat(surface.y(50.0, 100.0)).isWithin(1e-3).of(160.0)
    }

    @Test fun aLineFollowingABowedPage_comesOutStraight() {
        val bow = -20f
        val bent = square.copy(bends = listOf(PagePoint(0f, bow), PagePoint(0f, 0f), PagePoint(0f, bow), PagePoint(0f, 0f)))
        val photo = PagePixels(
            IntArray(200 * 200) { index ->
                val x = index % 200 + 0.5f
                val y = index / 200 + 0.5f
                val across = (x - 20f) / 160f
                val line = 100f + bow * 4 * across * (1 - across)
                if (abs(y - line) < 4f) BLACK else WHITE
            },
            200,
            200,
        )

        val page = warp(photo, bent, 80, 60)

        val rows = (4 until 76).map { x ->
            (0 until 60).filter { y -> page.pixels[y * 80 + x] and 0xFF < 128 }.average()
        }
        assertThat(rows.max() - rows.min()).isLessThan(1.5)
    }

    private companion object {
        const val TOP_HANDLE = 4
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
