package io.github.martindavinci.lunaris.scanner.encode

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.datamatrix.encoder.SymbolShapeHint
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.martindavinci.lunaris.scanner.model.CodeFormat

/** Draws text as a code. Run [CodeValidator] first: this throws [IllegalArgumentException] on bad text. */
object CodeEncoder {
    private const val CHARSET = "UTF-8"
    private const val QR_QUIET_MODULES = 4

    /** ZXing splits this over both sides of a linear code. */
    private const val LINEAR_MARGIN_MODULES = 20
    private const val LINEAR_WIDTH_PER_HEIGHT = 2

    /** ZXing leaves the border of these symbols to the caller, so it is added here. */
    private const val SYMBOL_QUIET_MODULES = 2

    /** Asks ZXing for one pixel per module, to be scaled here together with the border. */
    private const val ONE_PIXEL_PER_MODULE = 0

    val encodableFormats: List<CodeFormat> = listOf(
        CodeFormat.QR_CODE,
        CodeFormat.AZTEC,
        CodeFormat.DATA_MATRIX,
        CodeFormat.PDF_417,
        CodeFormat.EAN_13,
        CodeFormat.EAN_8,
        CodeFormat.UPC_A,
        CodeFormat.UPC_E,
        CodeFormat.CODE_128,
        CodeFormat.CODE_39,
        CodeFormat.CODE_93,
        CodeFormat.CODABAR,
        CodeFormat.ITF,
    )

    /**
     * The result may differ from [sizePx]: linear codes are [sizePx] wide and shorter than that, and
     * symbols that do not divide evenly into pixels are smaller. A symbol that cannot fit is drawn
     * at one pixel per module, so it comes out larger. Render the matrix at its own width and height.
     */
    fun encode(text: String, format: CodeFormat, sizePx: Int): BitMatrix {
        require(format in encodableFormats) { "$format cannot be encoded" }
        require(sizePx > 0) { "Size must be positive, got $sizePx" }
        return try {
            when (format) {
                CodeFormat.QR_CODE -> write(text, format, sizePx, sizePx, qrHints)
                CodeFormat.AZTEC, CodeFormat.DATA_MATRIX, CodeFormat.PDF_417 ->
                    withQuietZone(write(text, format, ONE_PIXEL_PER_MODULE, ONE_PIXEL_PER_MODULE, symbolHints), sizePx)
                else -> write(text, format, sizePx, sizePx / LINEAR_WIDTH_PER_HEIGHT, linearHints)
            }
        } catch (e: WriterException) {
            throw IllegalArgumentException("Cannot encode this text as $format", e)
        } catch (e: IllegalStateException) {
            throw IllegalArgumentException("Cannot encode this text as $format", e)
        }
    }

    private fun write(text: String, format: CodeFormat, width: Int, height: Int, hints: Map<EncodeHintType, Any>) =
        MultiFormatWriter().encode(text, BarcodeFormat.valueOf(format.name), width, height, hints)

    private val qrHints: Map<EncodeHintType, Any> = mapOf(
        EncodeHintType.CHARACTER_SET to CHARSET,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to QR_QUIET_MODULES,
    )

    private val linearHints: Map<EncodeHintType, Any> = mapOf(EncodeHintType.MARGIN to LINEAR_MARGIN_MODULES)

    /** The compact encoder is the only Data Matrix path in ZXing that takes more than Latin-1. */
    private val symbolHints: Map<EncodeHintType, Any> = mapOf(
        EncodeHintType.CHARACTER_SET to CHARSET,
        EncodeHintType.MARGIN to 0,
        EncodeHintType.DATA_MATRIX_COMPACT to true,
        EncodeHintType.DATA_MATRIX_SHAPE to SymbolShapeHint.FORCE_SQUARE,
    )

    private fun withQuietZone(modules: BitMatrix, sizePx: Int): BitMatrix {
        val fullWidth = modules.width + 2 * SYMBOL_QUIET_MODULES
        val fullHeight = modules.height + 2 * SYMBOL_QUIET_MODULES
        val scale = minOf(sizePx / fullWidth, sizePx / fullHeight).coerceAtLeast(1)
        val output = BitMatrix(fullWidth * scale, fullHeight * scale)
        for (y in 0 until modules.height) {
            for (x in 0 until modules.width) {
                if (modules[x, y]) {
                    val left = (x + SYMBOL_QUIET_MODULES) * scale
                    val top = (y + SYMBOL_QUIET_MODULES) * scale
                    output.setRegion(left, top, scale, scale)
                }
            }
        }
        return output
    }
}
