package io.github.martindavinci.lunaris.scanner.scan

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import java.util.EnumSet
import javax.inject.Inject

/**
 * Reads codes with ZXing. A reader keeps state between calls, so every read makes its own and one
 * decoder can be used from any thread.
 *
 * There is deliberately no CHARACTER_SET hint: it would override the encoding a code declares and
 * garble every code written in another one. Without it ZXing follows the code's ECI and otherwise
 * tells UTF-8, Shift_JIS and ISO-8859-1 apart from the bytes.
 */
class CodeDecoder @Inject constructor() {
    /** Tries the image as it is, then inverted. */
    fun decode(luminance: ByteArray, width: Int, height: Int): ScannedCode? {
        if (!isValidSize(luminance.size, width, height)) return null
        val source = PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false)
        return read(source) ?: read(source.invert())
    }

    /** Like [decode], then a quarter turned: ZXing reads barcodes only along the pixel rows. */
    fun decodeArgb(pixels: IntArray, width: Int, height: Int): ScannedCode? {
        if (!isValidSize(pixels.size, width, height)) return null
        val luminance = argbToLuminance(pixels, width * height)
        return decode(luminance, width, height) ?: decode(rotate90(luminance, width, height), height, width)
    }

    private fun isValidSize(available: Int, width: Int, height: Int) =
        width > 0 && height > 0 && available >= width * height

    // Damaged data can also surface as a runtime exception from inside ZXing.
    private fun read(source: LuminanceSource): ScannedCode? = try {
        val result = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), HINTS)
        result.toScannedCode()
            ?.takeIf { !it.isTooShortToTrust() }
            ?.takeIf { !it.format.isWeaklyChecked || BarRowCheck.confirms(result, source, HINTS) }
    } catch (_: ReaderException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    /** With no check character, a couple of Code 39 symbols are as likely to be a stretch of another code. */
    private fun ScannedCode.isTooShortToTrust() = format == CodeFormat.CODE_39 && text.length < MIN_CODE_39_LENGTH

    private fun Result.toScannedCode(): ScannedCode? {
        val format = FORMATS[barcodeFormat] ?: return null
        return text?.takeIf { it.isNotEmpty() }?.let { ScannedCode(it, format) }
    }

    private companion object {
        const val MIN_CODE_39_LENGTH = 3

        val FORMATS: Map<BarcodeFormat, CodeFormat> =
            CodeFormat.entries.associateBy { BarcodeFormat.valueOf(it.name) }

        val HINTS: Map<DecodeHintType, Any> = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to EnumSet.copyOf(FORMATS.keys),
            DecodeHintType.TRY_HARDER to true,
            // The start and stop letters are part of what a Codabar says; ZXing drops them by default.
            DecodeHintType.RETURN_CODABAR_START_END to true,
        )
    }
}
