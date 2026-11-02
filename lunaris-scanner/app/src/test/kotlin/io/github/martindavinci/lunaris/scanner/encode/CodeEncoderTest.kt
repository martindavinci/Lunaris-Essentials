package io.github.martindavinci.lunaris.scanner.encode

import com.google.common.truth.Truth.assertThat
import com.google.zxing.BarcodeFormat
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import org.junit.Assert.assertThrows
import org.junit.Test

class CodeEncoderTest {
    private val twoDimensional =
        listOf(CodeFormat.QR_CODE, CodeFormat.AZTEC, CodeFormat.DATA_MATRIX, CodeFormat.PDF_417)

    private fun assertRoundTrip(format: CodeFormat, text: String, expected: String = text) {
        val zxingFormat = BarcodeFormat.valueOf(format.name)
        val result = readBack(CodeEncoder.encode(text, format, SIZE_PX), zxingFormat)
        assertThat(result.text).isEqualTo(expected)
        assertThat(result.barcodeFormat).isEqualTo(zxingFormat)
    }

    @Test
    fun encodableFormatsAreTheAgreedOnes() {
        assertThat(CodeEncoder.encodableFormats).containsExactly(
            CodeFormat.QR_CODE, CodeFormat.AZTEC, CodeFormat.DATA_MATRIX, CodeFormat.PDF_417,
            CodeFormat.EAN_13, CodeFormat.EAN_8, CodeFormat.UPC_A, CodeFormat.UPC_E,
            CodeFormat.CODE_128, CodeFormat.CODE_39, CodeFormat.CODE_93, CodeFormat.CODABAR, CodeFormat.ITF,
        )
    }

    @Test
    fun twoDimensionalFormatsRoundTripAscii() {
        twoDimensional.forEach {
            assertRoundTrip(it, "https://example.com/?q=lunaris&n=1")
            assertRoundTrip(it, "1234567890")
        }
    }

    @Test
    fun twoDimensionalFormatsRoundTripUtf8() {
        twoDimensional.forEach {
            assertRoundTrip(it, "Città 東京 Москва")
            assertRoundTrip(it, "caffè")
        }
    }

    @Test
    fun qrAndAztecRoundTripEmoji() {
        assertRoundTrip(CodeFormat.QR_CODE, "Luna 🌙")
        assertRoundTrip(CodeFormat.AZTEC, "Luna 🌙")
    }

    @Test
    fun retailFormatsRoundTripAndWorkOutAMissingCheckDigit() {
        assertRoundTrip(CodeFormat.EAN_13, "4006381333931")
        assertRoundTrip(CodeFormat.EAN_13, "400638133393", "4006381333931")
        assertRoundTrip(CodeFormat.EAN_8, "96385074")
        assertRoundTrip(CodeFormat.EAN_8, "9638507", "96385074")
        assertRoundTrip(CodeFormat.UPC_A, "036000291452")
        assertRoundTrip(CodeFormat.UPC_A, "03600029145", "036000291452")
        assertRoundTrip(CodeFormat.UPC_E, "01234565")
        assertRoundTrip(CodeFormat.UPC_E, "0123456", "01234565")
    }

    @Test
    fun otherLinearFormatsRoundTrip() {
        assertRoundTrip(CodeFormat.CODE_128, "Lunaris-128 ~{}")
        assertRoundTrip(CodeFormat.CODE_128, "1")
        assertRoundTrip(CodeFormat.CODE_39, "MDV 1489")
        assertRoundTrip(CodeFormat.CODE_39, "A")
        assertRoundTrip(CodeFormat.CODE_93, "MDV-93 \$/+%")
        assertRoundTrip(CodeFormat.CODE_93, "A")
        assertRoundTrip(CodeFormat.ITF, "123456")
        assertRoundTrip(CodeFormat.ITF, "12345678901234")
    }

    @Test
    fun codabarRoundTripsWithoutItsGuards() {
        assertRoundTrip(CodeFormat.CODABAR, "1234-5678")
        assertRoundTrip(CodeFormat.CODABAR, "12")
        assertRoundTrip(CodeFormat.CODABAR, "B1234-5678D", "1234-5678")
    }

    @Test
    fun longestLinearTextsStillRoundTrip() {
        assertRoundTrip(CodeFormat.CODE_128, "x".repeat(LINEAR_LIMIT))
        assertRoundTrip(CodeFormat.CODE_39, "Z".repeat(LINEAR_LIMIT))
        assertRoundTrip(CodeFormat.CODE_93, "Z".repeat(LINEAR_LIMIT))
        assertRoundTrip(CodeFormat.CODABAR, "1".repeat(LINEAR_LIMIT))
        assertRoundTrip(CodeFormat.ITF, "1".repeat(LINEAR_LIMIT))
    }

    @Test
    fun theCapacityLimitsAreEncodableInTheWorstCase() {
        val limits = mapOf(
            CodeFormat.QR_CODE to EncodeLimits.QR_CODE,
            CodeFormat.AZTEC to EncodeLimits.AZTEC,
            CodeFormat.DATA_MATRIX to EncodeLimits.DATA_MATRIX,
            CodeFormat.PDF_417 to EncodeLimits.PDF_417,
        )
        limits.forEach { (format, limit) ->
            val texts = listOf(
                HARDEST_ASCII.repeat(limit / HARDEST_ASCII.length),
                "è".repeat(limit / UTF8_LATIN_BYTES),
                "東".repeat(limit / UTF8_CJK_BYTES),
            )
            texts.forEach { text ->
                assertThat(CodeValidator.validate(text, format)).isEqualTo(ValidationResult.Valid)
                assertRoundTrip(format, text)
            }
        }
    }

    @Test
    fun everyTwoDimensionalSymbolHasAWhiteBorder() {
        twoDimensional.forEach { format ->
            val matrix = CodeEncoder.encode("border", format, SIZE_PX)
            val border = (0 until matrix.width).flatMap { x -> listOf(matrix[x, 0], matrix[x, matrix.height - 1]) } +
                (0 until matrix.height).flatMap { y -> listOf(matrix[0, y], matrix[matrix.width - 1, y]) }
            assertThat(border).doesNotContain(true)
        }
    }

    @Test
    fun linearFormatsAreWiderThanTallWithAQuietZone() {
        val matrix = CodeEncoder.encode("4006381333931", CodeFormat.EAN_13, SIZE_PX)
        assertThat(matrix.width).isEqualTo(SIZE_PX)
        assertThat(matrix.height).isLessThan(SIZE_PX)
        val firstBar = (0 until matrix.width).first { matrix[it, 0] }
        val moduleWidth = SIZE_PX / EAN_13_MODULES_WITH_QUIET_ZONE
        assertThat(firstBar).isAtLeast(moduleWidth * MIN_QUIET_MODULES)
    }

    @Test
    fun qrFillsTheRequestedSize() {
        val matrix = CodeEncoder.encode("size", CodeFormat.QR_CODE, SIZE_PX)
        assertThat(matrix.width).isEqualTo(SIZE_PX)
        assertThat(matrix.height).isEqualTo(SIZE_PX)
    }

    @Test
    fun otherSymbolsFitTheRequestedSizeWhenItIsLargeEnough() {
        listOf(CodeFormat.AZTEC, CodeFormat.DATA_MATRIX, CodeFormat.PDF_417).forEach { format ->
            val matrix = CodeEncoder.encode("size", format, SIZE_PX)
            assertThat(matrix.width).isAtMost(SIZE_PX)
            assertThat(matrix.height).isAtMost(SIZE_PX)
            assertThat(matrix.width).isGreaterThan(SIZE_PX / 2)
        }
    }

    @Test
    fun aSizeTooSmallForTheSymbolStillGivesAWholeSymbol() {
        twoDimensional.forEach { format ->
            assertThat(CodeEncoder.encode("tiny", format, 1).width).isGreaterThan(1)
        }
    }

    @Test
    fun formatsThatCannotBeEncodedAreRejected() {
        listOf(CodeFormat.MAXICODE, CodeFormat.RSS_14, CodeFormat.RSS_EXPANDED).forEach {
            assertThrows(IllegalArgumentException::class.java) { CodeEncoder.encode("1", it, SIZE_PX) }
        }
    }

    @Test
    fun aSizeThatIsNotPositiveIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { CodeEncoder.encode("1", CodeFormat.QR_CODE, 0) }
    }

    @Test
    fun everyEncoderFailureIsAnIllegalArgument() {
        val tooLong = "x".repeat(MORE_THAN_ANY_SYMBOL_HOLDS)
        twoDimensional.forEach {
            assertThrows(IllegalArgumentException::class.java) { CodeEncoder.encode(tooLong, it, SIZE_PX) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            CodeEncoder.encode("4006381333930", CodeFormat.EAN_13, SIZE_PX)
        }
        listOf(CodeFormat.DATA_MATRIX, CodeFormat.PDF_417).forEach {
            assertThrows(IllegalArgumentException::class.java) { CodeEncoder.encode("🌙", it, SIZE_PX) }
        }
    }

    private companion object {
        const val SIZE_PX = 1024
        const val LINEAR_LIMIT = 80
        const val MORE_THAN_ANY_SYMBOL_HOLDS = 8000
        const val UTF8_LATIN_BYTES = 2
        const val UTF8_CJK_BYTES = 3
        const val EAN_13_MODULES_WITH_QUIET_ZONE = 115
        const val MIN_QUIET_MODULES = 9

        /** Keeps switching compaction modes: the costliest text measured for PDF417. */
        const val HARDEST_ASCII = "a1!"
    }
}
