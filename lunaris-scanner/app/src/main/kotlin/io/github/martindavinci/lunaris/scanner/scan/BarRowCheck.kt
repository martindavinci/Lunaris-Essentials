package io.github.martindavinci.lunaris.scanner.scan

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.oned.MultiFormatOneDReader
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ZXing accepts a barcode from one row of pixels. Any row across a QR code is a run of light and
 * dark stripes, and a weakly checked barcode has one check digit at most, so sooner or later such
 * a row passes for one. Real bars run the height of the code: the same reading must also come back
 * from rows further up or down, far enough away to be in other rows of a QR code's modules.
 */
internal object BarRowCheck {
    /** Distances to the other rows, as a share of the barcode's width. */
    private val ROW_OFFSETS = listOf(0.05, -0.05, 0.1, -0.1, 0.16, -0.16)
    private const val MATCHES_NEEDED = 2
    private const val POINTS_ON_A_ROW = 2

    fun confirms(result: Result, source: LuminanceSource, hints: Map<DecodeHintType, Any>): Boolean {
        val points = result.resultPoints
        if (points == null || points.size < POINTS_ON_A_ROW) return false
        val span = abs(points.last().x - points.first().x)
        val hitRow = points.first().y
        return ROW_OFFSETS.asSequence()
            .map { (hitRow + it * span).roundToInt() }
            .filter { it in 0 until source.height }
            .count { readsTheSame(result, source, it, hints) } >= MATCHES_NEEDED
    }

    private fun readsTheSame(
        result: Result,
        source: LuminanceSource,
        row: Int,
        hints: Map<DecodeHintType, Any>,
    ): Boolean = try {
        val line = BinaryBitmap(HybridBinarizer(source.crop(0, row, source.width, 1)))
        MultiFormatOneDReader(hints).decode(line, hints).text == result.text
    } catch (_: ReaderException) {
        false
    }
}
