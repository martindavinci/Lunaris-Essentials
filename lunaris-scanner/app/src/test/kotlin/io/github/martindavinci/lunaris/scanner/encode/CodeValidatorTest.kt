package io.github.martindavinci.lunaris.scanner.encode

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import org.junit.Test

class CodeValidatorTest {
    private fun assertValid(format: CodeFormat, vararg texts: String) {
        texts.forEach { assertThat(CodeValidator.validate(it, format)).isEqualTo(ValidationResult.Valid) }
    }

    private fun assertInvalid(format: CodeFormat, reason: InvalidReason, vararg texts: String) {
        texts.forEach {
            assertThat(CodeValidator.validate(it, format)).isEqualTo(ValidationResult.Invalid(reason))
        }
    }

    @Test
    fun emptyTextIsInvalidInEveryFormat() {
        CodeFormat.entries.forEach { assertInvalid(it, InvalidReason.EMPTY, "") }
    }

    @Test
    fun ean13ChecksTheCheckDigitAndAllowsItOmitted() {
        assertValid(CodeFormat.EAN_13, "4006381333931", "5901234123457", "400638133393")
        assertInvalid(CodeFormat.EAN_13, InvalidReason.BAD_CHECK_DIGIT, "4006381333930")
        assertInvalid(CodeFormat.EAN_13, InvalidReason.BAD_LENGTH, "40063813339", "40063813339312")
        assertInvalid(CodeFormat.EAN_13, InvalidReason.BAD_CHARACTERS, "400638133393X", "4006-38133393")
    }

    @Test
    fun ean8ChecksTheCheckDigitAndAllowsItOmitted() {
        assertValid(CodeFormat.EAN_8, "96385074", "9638507")
        assertInvalid(CodeFormat.EAN_8, InvalidReason.BAD_CHECK_DIGIT, "96385075")
        assertInvalid(CodeFormat.EAN_8, InvalidReason.BAD_LENGTH, "963850", "963850741")
    }

    @Test
    fun upcAChecksTheCheckDigitAndAllowsItOmitted() {
        assertValid(CodeFormat.UPC_A, "036000291452", "03600029145")
        assertInvalid(CodeFormat.UPC_A, InvalidReason.BAD_CHECK_DIGIT, "036000291453")
        assertInvalid(CodeFormat.UPC_A, InvalidReason.BAD_LENGTH, "0360002914", "0360002914521")
    }

    @Test
    fun upcEChecksTheExpandedNumberAndTheNumberSystem() {
        assertValid(CodeFormat.UPC_E, "01234565", "0123456", "1234567")
        assertInvalid(CodeFormat.UPC_E, InvalidReason.BAD_CHECK_DIGIT, "01234566")
        assertInvalid(CodeFormat.UPC_E, InvalidReason.BAD_LENGTH, "012345", "012345650")
        assertInvalid(CodeFormat.UPC_E, InvalidReason.BAD_CHARACTERS, "2123456", "0123a56")
    }

    @Test
    fun onlyAsciiDigitsCountAsDigits() {
        assertInvalid(CodeFormat.EAN_8, InvalidReason.BAD_CHARACTERS, "٠١٢٣٤٥٦")
        assertInvalid(CodeFormat.ITF, InvalidReason.BAD_CHARACTERS, "٠١٢٣٤٥")
    }

    @Test
    fun itfNeedsAnEvenCountOfDigitsTheReaderAccepts() {
        assertValid(CodeFormat.ITF, "123456", "1".repeat(80))
        assertInvalid(CodeFormat.ITF, InvalidReason.BAD_LENGTH, "1234567", "1234")
        assertInvalid(CodeFormat.ITF, InvalidReason.BAD_CHARACTERS, "12345a")
        assertInvalid(CodeFormat.ITF, InvalidReason.TOO_LONG, "1".repeat(82))
    }

    @Test
    fun code39And93CarryCapitalsDigitsAndTheirSymbols() {
        listOf(CodeFormat.CODE_39, CodeFormat.CODE_93).forEach { format ->
            assertValid(format, "MDV 1489", "A-B.C \$D/E+F%", "1", "Z".repeat(80))
            assertInvalid(format, InvalidReason.BAD_CHARACTERS, "mdv", "A*B", "A_B", "CITTÀ")
            assertInvalid(format, InvalidReason.TOO_LONG, "Z".repeat(81))
        }
    }

    @Test
    fun code128CarriesPrintableAscii() {
        assertValid(CodeFormat.CODE_128, "Hello, World! ~", " ", "x".repeat(80))
        assertInvalid(CodeFormat.CODE_128, InvalidReason.BAD_CHARACTERS, "a\tb", "a\u007Fb", "caffè", "ñ")
        assertInvalid(CodeFormat.CODE_128, InvalidReason.TOO_LONG, "x".repeat(81))
    }

    @Test
    fun codabarAcceptsItsSymbolsWithMatchingGuardsOrNone() {
        assertValid(CodeFormat.CODABAR, "12", "1234-5678", "\$:/.+", "A123B", "T123E", "D1-2A")
        assertInvalid(
            CodeFormat.CODABAR,
            InvalidReason.BAD_CHARACTERS,
            "12X4",
            "A123",
            "123B",
            "A123T",
            "T123B",
            "a123b",
        )
        assertInvalid(CodeFormat.CODABAR, InvalidReason.BAD_LENGTH, "1", "AB", "A1B")
        assertValid(CodeFormat.CODABAR, "1".repeat(80), "A" + "1".repeat(78) + "A")
        assertInvalid(CodeFormat.CODABAR, InvalidReason.TOO_LONG, "1".repeat(81))
    }

    @Test
    fun twoDimensionalFormatsCountUtf8Bytes() {
        val limits = mapOf(
            CodeFormat.QR_CODE to EncodeLimits.QR_CODE,
            CodeFormat.AZTEC to EncodeLimits.AZTEC,
            CodeFormat.DATA_MATRIX to EncodeLimits.DATA_MATRIX,
            CodeFormat.PDF_417 to EncodeLimits.PDF_417,
        )
        limits.forEach { (format, limit) ->
            assertValid(format, "x".repeat(limit), "è".repeat(limit / 2))
            assertInvalid(format, InvalidReason.TOO_LONG, "x".repeat(limit + 1), "è".repeat(limit / 2 + 1))
        }
    }

    @Test
    fun emojiAreRefusedOnlyWhereZxingCannotEncodeThem() {
        val moon = "🌙"
        assertValid(CodeFormat.QR_CODE, moon)
        assertValid(CodeFormat.AZTEC, moon)
        assertInvalid(CodeFormat.DATA_MATRIX, InvalidReason.BAD_CHARACTERS, moon)
        assertInvalid(CodeFormat.PDF_417, InvalidReason.BAD_CHARACTERS, moon)
    }

    @Test
    fun formatsThatCannotBeEncodedOnlyRejectEmptyText() {
        listOf(CodeFormat.MAXICODE, CodeFormat.RSS_14, CodeFormat.RSS_EXPANDED).forEach {
            assertValid(it, "anything")
        }
    }
}
