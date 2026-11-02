package io.github.martindavinci.lunaris.scanner.scan

import com.google.common.truth.Truth.assertThat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import org.junit.Test
import java.nio.charset.Charset
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger

class CodeDecoderTest {
    private val decoder = CodeDecoder()

    private fun decode(image: CodeImage) = decoder.decode(image.luminance, image.width, image.height)

    /** Raw bytes in a QR byte segment with no ECI: the writer maps ISO-8859-1 characters to bytes one to one. */
    private fun qrWithBytes(text: String, charset: String) =
        codeImage(String(text.toByteArray(Charset.forName(charset)), Charsets.ISO_8859_1), BarcodeFormat.QR_CODE)

    @Test fun everyFormatZxingCanWrite_isRead() {
        val samples = mapOf(
            CodeFormat.QR_CODE to "hello scanner",
            CodeFormat.AZTEC to "aztec text",
            CodeFormat.DATA_MATRIX to "data matrix",
            CodeFormat.PDF_417 to "pdf417 text",
            CodeFormat.EAN_13 to "5901234123457",
            CodeFormat.EAN_8 to "96385074",
            CodeFormat.UPC_A to "036000291452",
            CodeFormat.UPC_E to "01234565",
            CodeFormat.CODE_128 to "CODE128-text",
            CodeFormat.CODE_39 to "CODE39",
            CodeFormat.CODE_93 to "CODE93",
            CodeFormat.CODABAR to "A123456B",
            CodeFormat.ITF to "1234567890",
        )
        for ((format, text) in samples) {
            val image = codeImage(text, BarcodeFormat.valueOf(format.name))

            assertThat(decode(image)).isEqualTo(ScannedCode(text, format))
        }
    }

    @Test fun veryShortCode39_isNotTrusted() {
        assertThat(decode(codeImage(".", BarcodeFormat.CODE_39))).isNull()
        assertThat(decode(codeImage("AB", BarcodeFormat.CODE_39))).isNull()
        assertThat(decode(codeImage("ABC", BarcodeFormat.CODE_39))).isEqualTo(ScannedCode("ABC", CodeFormat.CODE_39))
    }

    @Test fun codabar_keepsItsStartAndStopLetters() {
        val image = codeImage("A1234567B", BarcodeFormat.CODABAR)

        assertThat(decode(image)).isEqualTo(ScannedCode("A1234567B", CodeFormat.CODABAR))
    }

    @Test fun invertedCode_isRead() {
        val image = codeImage("white on black", BarcodeFormat.QR_CODE).inverted()

        assertThat(decode(image)).isEqualTo(ScannedCode("white on black", CodeFormat.QR_CODE))
    }

    @Test fun invertedBarcode_isRead() {
        val image = codeImage("5901234123457", BarcodeFormat.EAN_13).inverted()

        assertThat(decode(image)).isEqualTo(ScannedCode("5901234123457", CodeFormat.EAN_13))
    }

    @Test fun declaredCharset_isHonoured() {
        val text = "caffè 日本語"
        val image = codeImage(text, BarcodeFormat.QR_CODE, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))

        assertThat(decode(image)?.text).isEqualTo(text)
    }

    @Test fun undeclaredCharset_isDetected() {
        val utf8 = "caffè 日本語"
        val shortUtf8 = "è"
        val shiftJis = "こんにちは世界"
        val latin1 = "Città più"

        assertThat(decode(qrWithBytes(utf8, "UTF-8"))?.text).isEqualTo(utf8)
        assertThat(decode(qrWithBytes(shortUtf8, "UTF-8"))?.text).isEqualTo(shortUtf8)
        assertThat(decode(qrWithBytes(shiftJis, "Shift_JIS"))?.text).isEqualTo(shiftJis)
        assertThat(decode(qrWithBytes(latin1, "ISO-8859-1"))?.text).isEqualTo(latin1)
    }

    @Test fun imageWithoutCode_isNull() {
        val blank = ByteArray(BLANK_SIDE * BLANK_SIDE) { WHITE_LUMA }
        val noise = ByteArray(BLANK_SIDE * BLANK_SIDE).also { Random(NOISE_SEED).nextBytes(it) }

        assertThat(decoder.decode(blank, BLANK_SIDE, BLANK_SIDE)).isNull()
        assertThat(decoder.decode(noise, BLANK_SIDE, BLANK_SIDE)).isNull()
    }

    @Test fun impossibleSizes_areNullInsteadOfThrowing() {
        val bytes = ByteArray(BLANK_SIDE)

        assertThat(decoder.decode(bytes, 0, 0)).isNull()
        assertThat(decoder.decode(bytes, -1, BLANK_SIDE)).isNull()
        assertThat(decoder.decode(bytes, BLANK_SIDE, BLANK_SIDE)).isNull()
        assertThat(decoder.decodeArgb(IntArray(BLANK_SIDE), BLANK_SIDE, BLANK_SIDE)).isNull()
        assertThat(decoder.decodeArgb(IntArray(BLANK_SIDE), 0, BLANK_SIDE)).isNull()
    }

    @Test fun argbPixels_areRead() {
        val image = codeImage("argb", BarcodeFormat.QR_CODE)
        val pixels = image.toArgb(light = OPAQUE_WHITE, dark = OPAQUE_BLACK)

        assertThat(decoder.decodeArgb(pixels, image.width, image.height))
            .isEqualTo(ScannedCode("argb", CodeFormat.QR_CODE))
    }

    @Test fun transparentBackground_readsAsWhite() {
        val image = codeImage("transparent", BarcodeFormat.QR_CODE)
        val pixels = image.toArgb(light = FULLY_TRANSPARENT, dark = OPAQUE_BLACK)

        assertThat(decoder.decodeArgb(pixels, image.width, image.height)?.text).isEqualTo("transparent")
    }

    @Test fun invertedArgbPixels_areRead() {
        val image = codeImage("argb inverted", BarcodeFormat.QR_CODE)
        val pixels = image.toArgb(light = OPAQUE_BLACK, dark = OPAQUE_WHITE)

        assertThat(decoder.decodeArgb(pixels, image.width, image.height)?.text).isEqualTo("argb inverted")
    }

    @Test fun sidewaysBarcodeInArgb_isRead() {
        val image = codeImage("5901234123457", BarcodeFormat.EAN_13).rotated()
        val pixels = image.toArgb(light = OPAQUE_WHITE, dark = OPAQUE_BLACK)

        assertThat(decoder.decodeArgb(pixels, image.width, image.height))
            .isEqualTo(ScannedCode("5901234123457", CodeFormat.EAN_13))
    }

    @Test fun oneDecoder_canBeUsedFromManyThreads() {
        val samples = listOf(
            codeImage("from a thread", BarcodeFormat.QR_CODE) to "from a thread",
            codeImage("5901234123457", BarcodeFormat.EAN_13) to "5901234123457",
        )
        val failures = AtomicInteger()

        val threads = List(THREADS) { index ->
            val (image, expected) = samples[index % samples.size]
            Thread {
                repeat(REPEATS) {
                    if (decode(image)?.text != expected) failures.incrementAndGet()
                }
            }.also { it.start() }
        }
        threads.forEach { it.join() }

        assertThat(failures.get()).isEqualTo(0)
    }

    private companion object {
        const val BLANK_SIDE = 200
        const val NOISE_SEED = 1L
        const val THREADS = 4
        const val REPEATS = 10
        const val OPAQUE_WHITE = -1
        const val OPAQUE_BLACK = 0xFF000000.toInt()
        const val FULLY_TRANSPARENT = 0
    }
}
