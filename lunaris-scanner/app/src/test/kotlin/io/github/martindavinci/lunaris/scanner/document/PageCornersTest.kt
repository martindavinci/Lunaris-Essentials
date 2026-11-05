package io.github.martindavinci.lunaris.scanner.document

import com.google.common.truth.Truth.assertThat
import org.junit.Test

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
}
