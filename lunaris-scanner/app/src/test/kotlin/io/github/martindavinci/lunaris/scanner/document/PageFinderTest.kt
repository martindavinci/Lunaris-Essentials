package io.github.martindavinci.lunaris.scanner.document

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class PageFinderTest {
    private val darkWood = Rgb(70, 48, 32)
    private val blueCloth = Rgb(30, 50, 110)
    private val paper = Rgb(235, 232, 225)
    private val skin = Rgb(205, 150, 120)

    private data class Rgb(val r: Int, val g: Int, val b: Int)

    private class Scene(val width: Int, val height: Int, background: Rgb, seed: Int = 1) {
        val random = Random(seed)
        val pixels = IntArray(width * height) { pack(background) }

        fun pack(c: Rgb): Int {
            fun near(v: Int) = (v + random.nextInt(-4, 5)).coerceIn(0, 255)
            return (0xFF shl 24) or (near(c.r) shl 16) or (near(c.g) shl 8) or near(c.b)
        }

        fun paint(c: Rgb, inside: (Float, Float) -> Boolean) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (inside(x + 0.5f, y + 0.5f)) pixels[y * width + x] = pack(c)
                }
            }
        }

        fun darken(share: Float, inside: (Float, Float) -> Boolean) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (inside(x + 0.5f, y + 0.5f)) {
                        val p = pixels[y * width + x]
                        fun dim(shift: Int) = ((p shr shift and 0xFF) * share).toInt()
                        pixels[y * width + x] = (0xFF shl 24) or (dim(16) shl 16) or (dim(8) shl 8) or dim(0)
                    }
                }
            }
        }

        fun photo() = PagePixels(pixels, width, height)
    }

    /** Lines of small dark marks across the middle of what [inside] covers. */
    private fun Scene.text(inside: (Float, Float) -> Boolean) {
        for (row in height / 4 until height * 3 / 4 step 14) {
            paint(Rgb(40, 40, 45)) { x, y ->
                y >= row && y < row + 4 && inside(x, y) && (x.toInt() / 9) % 4 != 3 && inside(x - 22, y) && inside(x + 22, y)
            }
        }
    }

    private fun quadInside(q: List<Pair<Float, Float>>): (Float, Float) -> Boolean = { x, y ->
        q.indices.all { i ->
            val (ax, ay) = q[i]
            val (bx, by) = q[(i + 1) % q.size]
            (bx - ax) * (y - ay) - (by - ay) * (x - ax) >= 0
        }
    }

    private fun assertCorners(found: PageCorners?, truth: List<Pair<Float, Float>>, width: Int, height: Int, within: Float = 0.015f) {
        assertThat(found).isNotNull()
        found!!.points.forEachIndexed { i, point ->
            assertThat(point.x).isWithin(within).of(truth[i].first / width)
            assertThat(point.y).isWithin(within).of(truth[i].second / height)
        }
    }

    private val tilted = listOf(90f to 70f, 400f to 40f, 440f to 560f, 60f to 590f)

    private fun tiltedScene(background: Rgb = darkWood): Scene {
        val scene = Scene(480, 640, background)
        val inside = quadInside(tilted)
        scene.paint(paper, inside)
        scene.text(inside)
        return scene
    }

    @Test fun aTiltedPageOnADarkTable_isFound() {
        assertCorners(findPage(tiltedScene().photo()), tilted, 480, 640)
    }

    @Test fun aPageOnAColouredCloth_isFound() {
        assertCorners(findPage(tiltedScene(blueCloth).photo()), tilted, 480, 640)
    }

    @Test fun aLargePhoto_isFoundJustTheSame() {
        val scene = Scene(1440, 1920, darkWood)
        val big = tilted.map { (x, y) -> x * 3 to y * 3 }
        scene.paint(paper, quadInside(big))

        assertCorners(findPage(scene.photo()), big, 1440, 1920)
    }

    @Test fun aFlatPage_hasStraightEdges() {
        val found = findPage(tiltedScene().photo())!!

        found.bends.forEach { bend ->
            assertThat(bend.x).isWithin(0.001f).of(0f)
            assertThat(bend.y).isWithin(0.001f).of(0f)
        }
    }

    @Test fun aSheetWithBowedEdges_isFoundWithItsBend() {
        val scene = Scene(480, 640, blueCloth)
        val left = 80f
        val right = 400f
        val bow = 30f
        fun bowed(x: Float): Float {
            val across = (x - left) / (right - left)
            return bow * 4 * across * (1 - across)
        }
        val inside = { x: Float, y: Float -> x in left..right && y >= 100f - bowed(x) && y <= 540f - bowed(x) }
        scene.paint(paper, inside)
        scene.text(inside)

        val found = findPage(scene.photo())

        assertCorners(found, listOf(left to 100f, right to 100f, right to 540f, left to 540f), 480, 640, within = 0.02f)
        assertThat(found!!.handles[TOP_EDGE].y).isWithin(0.015f).of(70f / 640)
        assertThat(found.handles[BOTTOM_EDGE].y).isWithin(0.015f).of(510f / 640)
        assertThat(abs(found.bends[1].x)).isLessThan(0.01f)
    }

    @Test fun aTiltedSheetWithBowedEdges_isFoundWithItsBend() {
        val scene = Scene(480, 640, blueCloth)
        val left = 90f
        val right = 390f
        val bow = 30f
        val turn = 0.14f
        fun upright(x: Float, y: Float) = (x - 240f) * cos(turn) + (y - 320f) * sin(turn) + 240f to
            -(x - 240f) * sin(turn) + (y - 320f) * cos(turn) + 320f
        fun onPhoto(x: Float, y: Float) = (x - 240f) * cos(turn) - (y - 320f) * sin(turn) + 240f to
            (x - 240f) * sin(turn) + (y - 320f) * cos(turn) + 320f
        val inside = { x: Float, y: Float ->
            val (ux, uy) = upright(x, y)
            val across = (ux - left) / (right - left)
            ux in left..right && uy >= 110f - bow * 4 * across * (1 - across) && uy <= 530f - bow * 4 * across * (1 - across)
        }
        scene.paint(paper, inside)
        scene.text(inside)
        val truth = listOf(onPhoto(left, 110f), onPhoto(right, 110f), onPhoto(right, 530f), onPhoto(left, 530f))

        val found = findPage(scene.photo())

        assertCorners(found, truth, 480, 640, within = 0.025f)
        assertThat(abs(found!!.bends[0].x) + abs(found.bends[0].y)).isGreaterThan(0.02f)
    }

    @Test fun aHardShadowReachingAnEdge_doesNotMoveTheCorners() {
        val scene = tiltedScene()
        scene.darken(0.5f) { x, y ->
            val dx = x - 260f
            val dy = y - 600f
            dx * dx / 8100 + dy * dy / 22500 < 1
        }

        assertCorners(findPage(scene.photo()), tilted, 480, 640, within = 0.02f)
    }

    @Test fun aThumbOverAnEdge_doesNotMoveIt() {
        val scene = tiltedScene()
        scene.paint(skin) { x, y ->
            val dx = x - 250f
            val dy = y - 600f
            dx * dx / 2500 + dy * dy / 8100 < 1
        }

        assertCorners(findPage(scene.photo()), tilted, 480, 640, within = 0.02f)
    }

    @Test fun aNarrowReceipt_isFound() {
        val receipt = listOf(200f to 60f, 280f to 70f, 270f to 580f, 190f to 570f)
        val scene = Scene(480, 640, Rgb(50, 54, 58))
        scene.paint(paper, quadInside(receipt))

        assertCorners(findPage(scene.photo()), receipt, 480, 640)
    }

    @Test fun aPanorama_findsNothingAndDoesNotFail() {
        assertThat(findPage(PagePixels(IntArray(2000 * 4) { -1 }, 2000, 4))).isNull()
        assertThat(findPage(PagePixels(IntArray(4 * 2000) { -1 }, 4, 2000))).isNull()
    }

    @Test fun aSinglePixel_findsNothingAndDoesNotFail() {
        assertThat(findPage(PagePixels(IntArray(1) { -1 }, 1, 1))).isNull()
    }

    @Test fun aBareTable_findsNothing() {
        assertThat(findPage(Scene(480, 640, darkWood).photo())).isNull()
    }

    @Test fun blotchesOnATable_findNothing() {
        val scene = Scene(480, 640, darkWood)
        val random = Random(5)
        repeat(60) {
            val cx = random.nextInt(480).toFloat()
            val cy = random.nextInt(640).toFloat()
            val shade = random.nextInt(30, 110)
            scene.paint(Rgb(shade, shade - 10, shade - 20)) { x, y -> (x - cx) * (x - cx) + (y - cy) * (y - cy) < 500 }
        }

        assertThat(findPage(scene.photo())).isNull()
    }

    @Test fun aPageFillingTheWholePhoto_findsNothing() {
        val scene = Scene(480, 640, paper)
        scene.text { _, _ -> true }

        assertThat(findPage(scene.photo())).isNull()
    }

    @Test fun aPageCutByTheFrame_findsNothing() {
        val scene = Scene(480, 640, darkWood)
        val inside = quadInside(listOf(120f to 100f, 600f to 100f, 600f to 800f, 120f to 800f))
        scene.paint(paper, inside)

        assertThat(findPage(scene.photo())).isNull()
    }

    @Test fun whitePaperOnAWhiteTable_findsNothing() {
        val scene = Scene(480, 640, paper)
        scene.text(quadInside(tilted))

        assertThat(findPage(scene.photo())).isNull()
    }

    private companion object {
        const val TOP_EDGE = 4
        const val BOTTOM_EDGE = 6
    }
}
