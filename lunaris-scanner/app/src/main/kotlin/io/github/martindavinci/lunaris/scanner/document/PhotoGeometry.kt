package io.github.martindavinci.lunaris.scanner.document

import kotlin.math.max
import kotlin.math.roundToInt

/** Photos are worked at up to this: far more than a page at 300 dots per inch needs, and it keeps memory in hand. */
const val WORK_LONG_SIDE = 4000

private const val SAMPLE_STEP = 2
private const val QUARTER = 90
private const val HALF_TURN = 180
private const val THREE_QUARTERS = 270

/** How EXIF says an image should stand: the mirror first, then the turn. */
data class ExifTurn(val turn: Int, val mirrored: Boolean) {
    companion object {
        private const val MIRROR_HORIZONTAL = 2
        private const val ROTATE_180 = 3
        private const val MIRROR_VERTICAL = 4
        private const val TRANSPOSE = 5
        private const val ROTATE_90 = 6
        private const val TRANSVERSE = 7
        private const val ROTATE_270 = 8

        fun of(orientation: Int): ExifTurn = when (orientation) {
            MIRROR_HORIZONTAL -> ExifTurn(0, true)
            ROTATE_180 -> ExifTurn(HALF_TURN, false)
            MIRROR_VERTICAL -> ExifTurn(HALF_TURN, true)
            TRANSPOSE -> ExifTurn(THREE_QUARTERS, true)
            ROTATE_90 -> ExifTurn(QUARTER, false)
            TRANSVERSE -> ExifTurn(QUARTER, true)
            ROTATE_270 -> ExifTurn(THREE_QUARTERS, false)
            else -> ExifTurn(0, false)
        }
    }
}

/** The largest power-of-two sample size that still leaves the long side at [longSide] or more. */
internal fun workSampleSize(width: Int, height: Int, longSide: Int): Int {
    val longest = max(width, height)
    var sample = 1
    while (longest / (sample * SAMPLE_STEP) >= longSide) sample *= SAMPLE_STEP
    return sample
}

internal fun workSize(width: Int, height: Int, longSide: Int): PageSize {
    val longest = max(width, height)
    if (longest <= longSide) return PageSize(width, height)
    val scale = longSide.toFloat() / longest
    return PageSize(max(1, (width * scale).roundToInt()), max(1, (height * scale).roundToInt()))
}
