package io.github.martindavinci.lunaris.scanner.scan

import java.nio.ByteBuffer

private const val OPAQUE = 255
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val CHANNEL_MASK = 0xFF
private const val GREEN_WEIGHT = 2
private const val WEIGHT_SUM = 4

/**
 * The luminance of a camera frame, tightly packed. The Y plane of YUV_420_888 has one byte per
 * pixel, but each row can be followed by padding up to [rowStride].
 */
internal fun copyLuminance(
    plane: ByteBuffer,
    rowStride: Int,
    width: Int,
    height: Int,
    left: Int = 0,
    top: Int = 0,
): ByteArray {
    val out = ByteArray(width * height)
    val source = plane.duplicate()
    for (row in 0 until height) {
        source.position((top + row) * rowStride + left)
        source.get(out, row * width, width)
    }
    return out
}

/** The image turned a quarter clockwise. The result is [height] wide and [width] high. */
internal fun rotate90(luminance: ByteArray, width: Int, height: Int): ByteArray {
    val out = ByteArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            out[x * height + (height - 1 - y)] = luminance[y * width + x]
        }
    }
    return out
}

/**
 * The first [count] pixels as luminance, using ZXing's weights (r + 2g + b) / 4. Transparency is
 * blended over white: a code saved with a transparent background has black in its clear pixels.
 */
internal fun argbToLuminance(pixels: IntArray, count: Int): ByteArray = ByteArray(count) { index ->
    val pixel = pixels[index]
    val alpha = (pixel ushr ALPHA_SHIFT) and CHANNEL_MASK
    val red = (pixel shr RED_SHIFT) and CHANNEL_MASK
    val green = (pixel shr GREEN_SHIFT) and CHANNEL_MASK
    val blue = pixel and CHANNEL_MASK
    val gray = (red + GREEN_WEIGHT * green + blue) / WEIGHT_SUM
    ((gray * alpha + OPAQUE * (OPAQUE - alpha)) / OPAQUE).toByte()
}
