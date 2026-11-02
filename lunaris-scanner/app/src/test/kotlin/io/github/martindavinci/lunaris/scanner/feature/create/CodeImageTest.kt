package io.github.martindavinci.lunaris.scanner.feature.create

import com.google.common.truth.Truth.assertThat
import com.google.zxing.common.BitMatrix
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CodeImageTest {
    private fun CodeImage.pixelAt(x: Int, y: Int) = pixels[y * width + x]

    private fun pattern() = BitMatrix(4, 2).apply {
        set(0, 0)
        set(3, 1)
    }

    @Test fun oneToOne_keepsDarkAndWhitePixels() {
        val image = CodeImage.of(pattern(), 4)

        assertThat(image.width).isEqualTo(4)
        assertThat(image.height).isEqualTo(2)
        assertThat(image.pixelAt(0, 0)).isEqualTo(CodeImage.DARK)
        assertThat(image.pixelAt(1, 0)).isEqualTo(CodeImage.LIGHT)
        assertThat(image.pixelAt(0, 1)).isEqualTo(CodeImage.LIGHT)
        assertThat(image.pixelAt(3, 1)).isEqualTo(CodeImage.DARK)
    }

    @Test fun scalesByTheLargestWholeFactorWithinTheTarget() {
        val image = CodeImage.of(pattern(), 15)

        assertThat(image.width).isEqualTo(12)
        assertThat(image.height).isEqualTo(6)
        for (x in 0 until 3) {
            for (y in 0 until 3) {
                assertThat(image.pixelAt(x, y)).isEqualTo(CodeImage.DARK)
            }
        }
        assertThat(image.pixelAt(3, 0)).isEqualTo(CodeImage.LIGHT)
        assertThat(image.pixelAt(0, 3)).isEqualTo(CodeImage.LIGHT)
        assertThat(image.pixelAt(9, 3)).isEqualTo(CodeImage.DARK)
        assertThat(image.pixelAt(11, 5)).isEqualTo(CodeImage.DARK)
    }

    @Test fun neverShrinksBelowOnePixelPerModule() {
        val image = CodeImage.of(pattern(), 1)

        assertThat(image.width).isEqualTo(4)
        assertThat(image.height).isEqualTo(2)
    }

    @Test fun renderer_producesOnlyDarkAndWhitePixels() = runTest {
        val image = ZxingCodeImageRenderer().render("hello", CodeFormat.QR_CODE, EXPORT_SIZE_PX)

        assertThat(image).isNotNull()
        assertThat(image!!.pixels.toSet()).containsExactly(CodeImage.DARK, CodeImage.LIGHT)
    }

    @Test fun renderer_returnsNullWhenTheContentDoesNotFit() = runTest {
        val tooLong = "x".repeat(TOO_LONG_FOR_ANY_QR_CODE)

        assertThat(ZxingCodeImageRenderer().render(tooLong, CodeFormat.QR_CODE, EXPORT_SIZE_PX)).isNull()
    }

    private companion object {
        const val TOO_LONG_FOR_ANY_QR_CODE = 8000
    }
}
