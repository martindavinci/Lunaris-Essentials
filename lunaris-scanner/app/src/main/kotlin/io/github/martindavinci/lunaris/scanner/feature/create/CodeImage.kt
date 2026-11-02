package io.github.martindavinci.lunaris.scanner.feature.create

import com.google.zxing.common.BitMatrix

/**
 * ARGB pixels of an encoded code: always dark modules on white, whatever the app theme, or the code
 * would not scan from a dark screen. Plain arrays, so the ViewModel layer stays free of Android.
 */
class CodeImage(val pixels: IntArray, val width: Int, val height: Int) {
    companion object {
        const val DARK = 0xFF000000.toInt()
        const val LIGHT = 0xFFFFFFFF.toInt()

        /**
         * Scales [matrix] by the largest whole factor that stays within [targetPx], never less than
         * one. A fractional factor would blur or drop modules.
         */
        fun of(matrix: BitMatrix, targetPx: Int): CodeImage {
            val scale = (targetPx / maxOf(matrix.width, matrix.height)).coerceAtLeast(1)
            val width = matrix.width * scale
            val height = matrix.height * scale
            val pixels = IntArray(width * height) { index ->
                val dark = matrix.get(index % width / scale, index / width / scale)
                if (dark) DARK else LIGHT
            }
            return CodeImage(pixels, width, height)
        }
    }
}
