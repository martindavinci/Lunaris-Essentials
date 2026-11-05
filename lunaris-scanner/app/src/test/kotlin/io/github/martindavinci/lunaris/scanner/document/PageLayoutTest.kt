package io.github.martindavinci.lunaris.scanner.document

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageLayoutTest {
    private fun rectangle(width: Float, height: Float) = PageCorners(
        PagePoint(0f, 0f),
        PagePoint(width, 0f),
        PagePoint(width, height),
        PagePoint(0f, height),
    )

    @Test fun asPhotographed_takesTheProportionsOfTheCorners() {
        assertThat(pageSize(rectangle(1000f, 2000f), PaperSize.AS_PHOTOGRAPHED, 3508)).isEqualTo(PageSize(1000, 2000))
    }

    @Test fun asPhotographed_shrinksToTheLongSide() {
        assertThat(pageSize(rectangle(1000f, 2000f), PaperSize.AS_PHOTOGRAPHED, 900)).isEqualTo(PageSize(450, 900))
    }

    @Test fun asPhotographed_averagesTheTwoEdges() {
        val quad = PageCorners(PagePoint(100f, 0f), PagePoint(300f, 0f), PagePoint(400f, 300f), PagePoint(0f, 300f))

        assertThat(pageSize(quad, PaperSize.AS_PHOTOGRAPHED, 3508).width).isEqualTo(300)
    }

    @Test fun a4_isItsRealSizeAt300Dpi() {
        assertThat(pageSize(rectangle(1000f, 2000f), PaperSize.A4, 3508)).isEqualTo(PageSize(2480, 3508))
    }

    @Test fun a4_keepsItsProportionsWhenSmaller() {
        assertThat(pageSize(rectangle(1000f, 2000f), PaperSize.A4, 900)).isEqualTo(PageSize(636, 900))
    }

    @Test fun letter_neverGoesPastItsOwn300Dpi() {
        assertThat(pageSize(rectangle(1000f, 2000f), PaperSize.LETTER, 3508)).isEqualTo(PageSize(2550, 3300))
    }

    @Test fun aWideQuad_liesThePaperOnItsSide() {
        assertThat(pageSize(rectangle(2000f, 1000f), PaperSize.A4, 3508)).isEqualTo(PageSize(3508, 2480))
    }

    @Test fun neverLessThanOnePixel() {
        assertThat(pageSize(rectangle(0.1f, 2000f), PaperSize.AS_PHOTOGRAPHED, 900).width).isEqualTo(1)
    }

    @Test fun pointsOfAPhotographedPage_comeFrom300Dpi() {
        val points = pagePoints(PageSize(300, 600), PaperSize.AS_PHOTOGRAPHED)

        assertThat(points.width).isWithin(0.001f).of(72f)
        assertThat(points.height).isWithin(0.001f).of(144f)
    }

    @Test fun pointsOfA4_areTheStandardOnes_turnedWithThePage() {
        assertThat(pagePoints(PageSize(2480, 3508), PaperSize.A4)).isEqualTo(PagePoints(595.28f, 841.89f))
        assertThat(pagePoints(PageSize(3508, 2480), PaperSize.A4)).isEqualTo(PagePoints(841.89f, 595.28f))
    }

    @Test fun pointsOfLetter_areTheStandardOnes() {
        assertThat(pagePoints(PageSize(2550, 3300), PaperSize.LETTER)).isEqualTo(PagePoints(612f, 792f))
    }
}
