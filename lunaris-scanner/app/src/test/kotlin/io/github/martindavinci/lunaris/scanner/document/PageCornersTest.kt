package io.github.martindavinci.lunaris.scanner.document

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class PageCornersTest {
    private fun corners(vararg xy: Float) = PageCorners(
        PagePoint(xy[0], xy[1]),
        PagePoint(xy[2], xy[3]),
        PagePoint(xy[4], xy[5]),
        PagePoint(xy[6], xy[7]),
    )

    @Test fun startingCorners_sitJustInsideThePhoto() {
        val start = PageCorners.START

        assertThat(start.points[0]).isEqualTo(PagePoint(0.06f, 0.06f))
        assertThat(start.points[2]).isEqualTo(PagePoint(0.94f, 0.94f))
        assertThat(start.problem()).isNull()
    }

    @Test fun crossedCorners_areAProblem() {
        assertThat(corners(0.9f, 0.1f, 0.1f, 0.1f, 0.9f, 0.9f, 0.1f, 0.9f).problem()).isEqualTo(CornersProblem.CROSSED)
    }

    @Test fun aCornerPushedInsideThePage_isAProblem() {
        assertThat(corners(0.1f, 0.1f, 0.9f, 0.1f, 0.5f, 0.3f, 0.1f, 0.9f).problem()).isEqualTo(CornersProblem.CROSSED)
    }

    @Test fun cornersInALine_areAProblem() {
        assertThat(corners(0.1f, 0.5f, 0.4f, 0.5f, 0.7f, 0.5f, 0.9f, 0.5f).problem()).isEqualTo(CornersProblem.CROSSED)
    }

    @Test fun cornersEnclosingAlmostNothing_areAProblem() {
        assertThat(corners(0.5f, 0.5f, 0.55f, 0.5f, 0.55f, 0.55f, 0.5f, 0.55f).problem()).isEqualTo(CornersProblem.TOO_SMALL)
    }

    @Test fun cornersGoneRoundTheOtherWay_areNoProblem() {
        assertThat(corners(0.1f, 0.1f, 0.1f, 0.9f, 0.9f, 0.9f, 0.9f, 0.1f).problem()).isNull()
    }

    @Test fun moved_changesOnlyThatCorner_andStaysOnThePhoto() {
        val moved = PageCorners.START.moved(2, PagePoint(1.4f, -0.2f))

        assertThat(moved.points[2]).isEqualTo(PagePoint(1f, 0f))
        assertThat(moved.points[0]).isEqualTo(PageCorners.START.points[0])
    }

    @Test fun nearest_findsTheCornerWithinReach() {
        val inPixels = corners(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f).inPixels(100, 100)

        assertThat(inPixels.nearest(PagePoint(95f, 96f), 20f)).isEqualTo(2)
        assertThat(inPixels.nearest(PagePoint(4f, 90f), 20f)).isEqualTo(3)
    }

    @Test fun nearest_findsNothingOutOfReach() {
        val inPixels = corners(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f).inPixels(100, 100)

        assertThat(inPixels.nearest(PagePoint(50f, 50f), 20f)).isNull()
    }

    @Test fun straightEdges_haveTheirHandlesHalfwayAlong() {
        val square = corners(0.1f, 0.1f, 0.9f, 0.1f, 0.9f, 0.9f, 0.1f, 0.9f)

        assertThat(square.handles).hasSize(8)
        assertThat(square.handles.take(4)).isEqualTo(square.points)
        assertClose(square.handles[4], PagePoint(0.5f, 0.1f))
        assertClose(square.handles[5], PagePoint(0.9f, 0.5f))
        assertClose(square.handles[6], PagePoint(0.5f, 0.9f))
        assertClose(square.handles[7], PagePoint(0.1f, 0.5f))
    }

    @Test fun inPerspective_theHalfwayHandleLiesTowardsTheFarEnd() {
        // The top edge recedes to the right: its right half looks shorter.
        val handle = corners(0f, 0f, 1f, 0.25f, 1f, 0.75f, 0f, 1f).handles[4]

        assertThat(handle.x).isGreaterThan(0.5f)
        assertThat(handle.y).isWithin(1e-4f).of(handle.x * 0.25f)
    }

    @Test fun movingAnEdgeHandle_bendsThatEdgeOnly() {
        val moved = PageCorners.START.moved(4, PagePoint(0.5f, 0.02f))

        assertClose(moved.handles[4], PagePoint(0.5f, 0.02f))
        assertThat(moved.points).isEqualTo(PageCorners.START.points)
        assertClose(moved.handles[6], PageCorners.START.handles[6])
    }

    @Test fun anEdgeHandle_staysOnThePhoto() {
        val moved = PageCorners.START.moved(5, PagePoint(1.5f, -0.3f))

        assertClose(moved.handles[5], PagePoint(1f, 0f))
    }

    @Test fun movingACorner_keepsTheBendOfItsEdge() {
        val bent = PageCorners.START.moved(4, PagePoint(0.5f, 0.02f))

        val moved = bent.moved(0, PagePoint(0.1f, 0.06f))

        assertThat(moved.bends).isEqualTo(bent.bends)
    }

    @Test fun inPixels_scalesTheBendsToo() {
        val bent = corners(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f).moved(4, PagePoint(0.5f, 0.2f))

        assertClose(bent.inPixels(200, 100).handles[4], PagePoint(100f, 20f))
    }

    @Test fun nearest_reachesEdgeHandlesToo() {
        val inPixels = corners(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f).inPixels(100, 100)

        assertThat(inPixels.nearest(PagePoint(50f, 96f), 20f)).isEqualTo(6)
    }

    @Test fun theOutline_goesThroughEveryHandleInOrder() {
        val bent = corners(0.1f, 0.1f, 0.9f, 0.1f, 0.9f, 0.9f, 0.1f, 0.9f).moved(4, PagePoint(0.5f, 0.02f))

        val outline = bent.outline(stepsPerEdge = 4)

        assertThat(outline).hasSize(16)
        assertClose(outline[0], bent.points[0])
        assertClose(outline[2], bent.handles[4])
        assertClose(outline[4], bent.points[1])
        assertClose(outline[8], bent.points[2])
        assertClose(outline[12], bent.points[3])
    }

    @Test fun aMildBend_isNoProblem() {
        assertThat(PageCorners.START.moved(4, PagePoint(0.5f, 0.15f)).problem()).isNull()
    }

    @Test fun anEdgeBentThroughTheOppositeOne_isAProblemOfThatEdge() {
        val bent = PageCorners.START.moved(4, PagePoint(0.5f, 1f))

        assertThat(bent.problem()).isEqualTo(CornersProblem.EDGE_CROSSED)
    }

    @Test fun onATiltedPage_oneBentEdge_isNoProblem() {
        val random = Random(3)
        repeat(200) {
            fun wobble() = random.nextFloat() * 0.12f
            val tilted = corners(
                0.1f + wobble(), 0.1f + wobble(),
                0.9f - wobble(), 0.08f + wobble(),
                0.92f - wobble(), 0.9f - wobble(),
                0.07f + wobble(), 0.88f - wobble(),
            )
            val top = tilted.handles[4]

            val bent = tilted.moved(4, PagePoint(top.x, top.y - 0.03f))

            assertThat(bent.problem()).isNull()
        }
    }

    @Test fun aPhotoFitsItsStage_centredAlongTheLongerWay() {
        val fit = PhotoFit.of(photoWidth = 200, photoHeight = 100, stageWidth = 400f, stageHeight = 400f)

        assertThat(fit.scale).isEqualTo(2f)
        assertThat(fit.left).isEqualTo(0f)
        assertThat(fit.top).isEqualTo(100f)
    }

    @Test fun aFit_goesFromStageToSharesAndBack() {
        val fit = PhotoFit.of(photoWidth = 200, photoHeight = 100, stageWidth = 400f, stageHeight = 400f)

        assertThat(fit.toStage(PagePoint(0.5f, 1f), 200, 100)).isEqualTo(PagePoint(200f, 300f))
        assertThat(fit.toShare(PagePoint(200f, 300f), 200, 100)).isEqualTo(PagePoint(0.5f, 1f))
    }

    @Test fun inPixels_scalesToThePhoto() {
        val pixels = corners(0f, 0f, 1f, 0f, 1f, 0.5f, 0f, 0.5f).inPixels(200, 100)

        assertThat(pixels.points[2]).isEqualTo(PagePoint(200f, 50f))
    }

    private fun assertClose(actual: PagePoint, expected: PagePoint) {
        assertThat(actual.x).isWithin(1e-4f).of(expected.x)
        assertThat(actual.y).isWithin(1e-4f).of(expected.y)
    }
}
