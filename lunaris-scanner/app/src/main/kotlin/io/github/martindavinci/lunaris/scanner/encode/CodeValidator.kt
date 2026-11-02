package io.github.martindavinci.lunaris.scanner.encode

import com.google.zxing.oned.UPCEReader
import io.github.martindavinci.lunaris.scanner.model.CodeFormat

sealed interface ValidationResult {
    data object Valid : ValidationResult

    data class Invalid(val reason: InvalidReason) : ValidationResult
}

enum class InvalidReason { EMPTY, TOO_LONG, BAD_CHARACTERS, BAD_LENGTH, BAD_CHECK_DIGIT }

/**
 * UTF-8 bytes every text of that size fits in, whatever it holds, measured against ZXing with the
 * hints [CodeEncoder] uses. Digits alone could go further, but the limit has to hold for any text.
 */
internal object EncodeLimits {
    const val QR_CODE = 2300
    const val AZTEC = 1800

    /** Stops short of the largest symbol, 144 x 144: ZXing writes it but its own reader cannot find it. */
    const val DATA_MATRIX = 1250
    const val PDF_417 = 850

    /** The longest text ZXing writes in a linear code, and already wider than any label. */
    const val LINEAR = 80
}

/**
 * Valid text does not make [CodeEncoder] throw. Text is checked as it is, with nothing trimmed or
 * raised to capitals. Formats outside [CodeEncoder.encodableFormats] only reject empty text.
 */
object CodeValidator {
    private const val EAN_13_LENGTH = 13
    private const val EAN_8_LENGTH = 8
    private const val UPC_A_LENGTH = 12
    private const val UPC_E_LENGTH = 8
    private const val UPC_E_NUMBER_SYSTEMS = "01"

    /** ZXing reads shorter ITF codes as false positives, so a code below this could not be scanned back. */
    private const val ITF_MIN_LENGTH = 6
    private const val CODABAR_MIN_BODY_LENGTH = 2

    private const val CODE_39_93_CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. \$/+%"
    private const val CODABAR_BODY_CHARACTERS = "0123456789-\$:/.+"
    private const val CODABAR_GUARDS = "ABCD"
    private const val CODABAR_ALTERNATE_GUARDS = "TN*E"
    private val CODE_128_CHARACTERS = ' '..'~'

    private const val CHECK_WEIGHT_NEAREST = 3
    private const val CHECK_WEIGHT_OTHER = 1
    private const val CHECK_BASE = 10

    fun validate(text: String, format: CodeFormat): ValidationResult {
        if (text.isEmpty()) return ValidationResult.Invalid(InvalidReason.EMPTY)
        val problem = when (format) {
            CodeFormat.QR_CODE -> capacityProblem(text, EncodeLimits.QR_CODE)
            CodeFormat.AZTEC -> capacityProblem(text, EncodeLimits.AZTEC)
            CodeFormat.DATA_MATRIX -> bmpOnlyProblem(text) ?: capacityProblem(text, EncodeLimits.DATA_MATRIX)
            CodeFormat.PDF_417 -> bmpOnlyProblem(text) ?: capacityProblem(text, EncodeLimits.PDF_417)
            CodeFormat.EAN_13 -> retailProblem(text, EAN_13_LENGTH)
            CodeFormat.EAN_8 -> retailProblem(text, EAN_8_LENGTH)
            CodeFormat.UPC_A -> retailProblem(text, UPC_A_LENGTH)
            CodeFormat.UPC_E -> upcEProblem(text)
            CodeFormat.CODE_128 -> linearProblem(text) { it in CODE_128_CHARACTERS }
            CodeFormat.CODE_39, CodeFormat.CODE_93 -> linearProblem(text) { it in CODE_39_93_CHARACTERS }
            CodeFormat.CODABAR -> codabarProblem(text)
            CodeFormat.ITF -> itfProblem(text)
            CodeFormat.MAXICODE, CodeFormat.RSS_14, CodeFormat.RSS_EXPANDED -> null
        }
        return problem?.let(ValidationResult::Invalid) ?: ValidationResult.Valid
    }

    private fun capacityProblem(text: String, limit: Int): InvalidReason? =
        InvalidReason.TOO_LONG.takeIf { text.encodeToByteArray().size > limit }

    /** ZXing's Data Matrix and PDF417 encoders look at one UTF-16 unit at a time, so emoji make them fail. */
    private fun bmpOnlyProblem(text: String): InvalidReason? =
        InvalidReason.BAD_CHARACTERS.takeIf { text.any(Char::isSurrogate) }

    private fun linearProblem(text: String, isAllowed: (Char) -> Boolean): InvalidReason? = when {
        !text.all(isAllowed) -> InvalidReason.BAD_CHARACTERS
        text.length > EncodeLimits.LINEAR -> InvalidReason.TOO_LONG
        else -> null
    }

    private fun retailProblem(text: String, fullLength: Int): InvalidReason? = when {
        !isDigits(text) -> InvalidReason.BAD_CHARACTERS
        text.length != fullLength && text.length != fullLength - 1 -> InvalidReason.BAD_LENGTH
        text.length == fullLength && !hasGoodCheckDigit(text) -> InvalidReason.BAD_CHECK_DIGIT
        else -> null
    }

    /** UPC-E has its check digit worked out on the UPC-A number it stands for. */
    private fun upcEProblem(text: String): InvalidReason? = when {
        !isDigits(text) -> InvalidReason.BAD_CHARACTERS
        text.length != UPC_E_LENGTH && text.length != UPC_E_LENGTH - 1 -> InvalidReason.BAD_LENGTH
        text.first() !in UPC_E_NUMBER_SYSTEMS -> InvalidReason.BAD_CHARACTERS
        text.length == UPC_E_LENGTH && !hasGoodCheckDigit(UPCEReader.convertUPCEtoUPCA(text)) ->
            InvalidReason.BAD_CHECK_DIGIT
        else -> null
    }

    private fun itfProblem(text: String): InvalidReason? = when {
        !isDigits(text) -> InvalidReason.BAD_CHARACTERS
        text.length > EncodeLimits.LINEAR -> InvalidReason.TOO_LONG
        text.length % 2 != 0 || text.length < ITF_MIN_LENGTH -> InvalidReason.BAD_LENGTH
        else -> null
    }

    /** Start and stop characters are optional, but if there is one there must be two of the same set. */
    private fun codabarProblem(text: String): InvalidReason? {
        val startsGuarded = isCodabarGuard(text.first())
        val endsGuarded = isCodabarGuard(text.last())
        val guarded = text.length >= 2 && startsGuarded && endsGuarded
        val body = if (guarded) text.substring(1, text.length - 1) else text
        return when {
            guarded && (text.first() in CODABAR_GUARDS) != (text.last() in CODABAR_GUARDS) ->
                InvalidReason.BAD_CHARACTERS
            !body.all { it in CODABAR_BODY_CHARACTERS } -> InvalidReason.BAD_CHARACTERS
            text.length > EncodeLimits.LINEAR -> InvalidReason.TOO_LONG
            body.length < CODABAR_MIN_BODY_LENGTH -> InvalidReason.BAD_LENGTH
            else -> null
        }
    }

    private fun isCodabarGuard(char: Char): Boolean = char in CODABAR_GUARDS || char in CODABAR_ALTERNATE_GUARDS

    private fun isDigits(text: String): Boolean = text.all { it in '0'..'9' }

    /** Weights run 3, 1, 3, 1 from the digit next to the check digit, for every EAN and UPC length. */
    private fun hasGoodCheckDigit(number: String): Boolean {
        val sum = number.dropLast(1).reversed().withIndex().sumOf { (index, digit) ->
            digit.digitToInt() * if (index % 2 == 0) CHECK_WEIGHT_NEAREST else CHECK_WEIGHT_OTHER
        }
        return (CHECK_BASE - sum % CHECK_BASE) % CHECK_BASE == number.last().digitToInt()
    }
}
