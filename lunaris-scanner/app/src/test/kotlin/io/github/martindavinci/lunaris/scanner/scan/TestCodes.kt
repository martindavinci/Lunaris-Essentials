package io.github.martindavinci.lunaris.scanner.scan

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

internal const val BLACK_LUMA: Byte = 0
internal const val WHITE_LUMA: Byte = -1

/** A code drawn as an 8 bit luminance image, with a quiet zone around it. */
internal class CodeImage(val luminance: ByteArray, val width: Int, val height: Int) {
    fun inverted() = CodeImage(ByteArray(luminance.size) { luminance[it].toInt().inv().toByte() }, width, height)

    fun rotated() = CodeImage(rotate90(luminance, width, height), height, width)

    fun toArgb(light: Int, dark: Int) =
        IntArray(luminance.size) { if (luminance[it] == WHITE_LUMA) light else dark }
}

internal fun codeImage(
    text: String,
    format: BarcodeFormat,
    hints: Map<EncodeHintType, Any> = emptyMap(),
): CodeImage {
    val is1d = format !in MATRIX_FORMATS
    val width = if (is1d) ONE_D_WIDTH else SIDE
    val height = if (is1d) ONE_D_HEIGHT else SIDE
    return MultiFormatWriter().encode(text, format, width, height, hints).toCodeImage()
}

private fun BitMatrix.toCodeImage(): CodeImage {
    val outWidth = width + 2 * QUIET_ZONE
    val outHeight = height + 2 * QUIET_ZONE
    val out = ByteArray(outWidth * outHeight) { WHITE_LUMA }
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (get(x, y)) out[(y + QUIET_ZONE) * outWidth + x + QUIET_ZONE] = BLACK_LUMA
        }
    }
    return CodeImage(out, outWidth, outHeight)
}

private val MATRIX_FORMATS = setOf(
    BarcodeFormat.QR_CODE,
    BarcodeFormat.AZTEC,
    BarcodeFormat.DATA_MATRIX,
    BarcodeFormat.PDF_417,
)
private const val SIDE = 300
private const val ONE_D_WIDTH = 400
private const val ONE_D_HEIGHT = 160
private const val QUIET_ZONE = 20
