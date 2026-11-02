package io.github.martindavinci.lunaris.scanner.encode

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

/** Reads a matrix the way the camera path does: no pure-barcode shortcut. */
internal fun readBack(matrix: BitMatrix, format: BarcodeFormat): Result {
    val pixels = IntArray(matrix.width * matrix.height) { index ->
        if (matrix[index % matrix.width, index / matrix.width]) BLACK else WHITE
    }
    val source = RGBLuminanceSource(matrix.width, matrix.height, pixels)
    val hints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(format),
    )
    return MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), hints)
}
