package io.github.martindavinci.lunaris.scanner.scan

import com.google.common.truth.Truth.assertThat
import com.google.zxing.BarcodeFormat
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import org.junit.Test
import java.util.Random

/** Blurred frames of a dense QR code gave UPC-E, EAN-8 and UPC-A readings before [BarRowCheck]. */
class FalseBarcodeTest {
    private val decoder = CodeDecoder()

    private fun formatsRead(text: String, format: BarcodeFormat, heightPerWidth: Double = 1.0): Set<CodeFormat> {
        val random = Random(SEED)
        return List(FRAMES) { decoder.decodeFrame(cameraFrame(text, format, random, heightPerWidth))?.format }
            .filterNotNull().toSet()
    }

    @Test fun denseQrCode_isNeverReadAsABarcode() {
        val text = "Lunaris Scanner legge anche testi lunghi. ".repeat(COPIES).take(LONG)

        assertThat(formatsRead(text, BarcodeFormat.QR_CODE) - CodeFormat.QR_CODE).isEmpty()
    }

    @Test fun realBarcodeFrames_giveOnlyItsOwnFormat() {
        assertThat(formatsRead("4006381333931", BarcodeFormat.EAN_13, BAR_HEIGHT)).containsExactly(CodeFormat.EAN_13)
        assertThat(formatsRead("A1234567B", BarcodeFormat.CODABAR, BAR_HEIGHT)).containsExactly(CodeFormat.CODABAR)
        assertThat(formatsRead("LUNARIS 39", BarcodeFormat.CODE_39, BAR_HEIGHT)).containsExactly(CodeFormat.CODE_39)
    }

    @Test fun lowBarcode_isStillRead() {
        assertThat(formatsRead("4006381333931", BarcodeFormat.EAN_13, LOW_BAR_HEIGHT)).containsExactly(CodeFormat.EAN_13)
    }

    private companion object {
        const val SEED = 7L
        const val FRAMES = 60
        const val COPIES = 40
        const val LONG = 1500
        const val BAR_HEIGHT = 0.6
        const val LOW_BAR_HEIGHT = 0.2
    }
}
