package io.github.martindavinci.lunaris.scanner.scan

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.Random

private const val FRAME_WIDTH = 1280
private const val FRAME_HEIGHT = 720
private const val BACKGROUND_GREY = 200
private const val DARK_MODULE = 0x101010
private const val LIGHT_MODULE = 0xF0F0F0
private const val MAX_EXTRA_BLUR = 3.0
private const val MAX_TILT_DEGREES = 12.0
private const val MIN_CODE_SHARE = 0.35
private const val CODE_SHARE_RANGE = 0.6

/** A code as a camera sees it: somewhat blurred, tilted and at a varying distance, in a 1280x720 frame. */
internal fun cameraFrame(
    text: String,
    format: BarcodeFormat,
    random: Random,
    heightPerWidth: Double = 1.0,
): ByteArray {
    val matrix = MultiFormatWriter().encode(text, format, 0, 0, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))
    val code = BufferedImage(matrix.width, matrix.height, BufferedImage.TYPE_BYTE_GRAY)
    for (y in 0 until matrix.height) {
        for (x in 0 until matrix.width) code.setRGB(x, y, if (matrix[x, y]) DARK_MODULE else LIGHT_MODULE)
    }
    val blur = 1.0 + random.nextDouble() * MAX_EXTRA_BLUR
    val small = BufferedImage((FRAME_WIDTH / blur).toInt(), (FRAME_HEIGHT / blur).toInt(), BufferedImage.TYPE_BYTE_GRAY)
    small.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        color = Color(BACKGROUND_GREY, BACKGROUND_GREY, BACKGROUND_GREY)
        fillRect(0, 0, small.width, small.height)
        val side = small.height * (MIN_CODE_SHARE + random.nextDouble() * CODE_SHARE_RANGE)
        val codeHeight = side * heightPerWidth
        translate(small.width / 2.0, small.height / 2.0)
        rotate(Math.toRadians(random.nextDouble() * 2 * MAX_TILT_DEGREES - MAX_TILT_DEGREES))
        drawImage(code, (-side / 2).toInt(), (-codeHeight / 2).toInt(), side.toInt(), codeHeight.toInt(), null)
        dispose()
    }
    val frame = BufferedImage(FRAME_WIDTH, FRAME_HEIGHT, BufferedImage.TYPE_BYTE_GRAY)
    frame.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        drawImage(small, 0, 0, FRAME_WIDTH, FRAME_HEIGHT, null)
        dispose()
    }
    return ByteArray(FRAME_WIDTH * FRAME_HEIGHT).also { frame.raster.getDataElements(0, 0, FRAME_WIDTH, FRAME_HEIGHT, it) }
}

internal fun CodeDecoder.decodeFrame(luminance: ByteArray) =
    decode(luminance, FRAME_WIDTH, FRAME_HEIGHT)
        ?: decode(rotate90(luminance, FRAME_WIDTH, FRAME_HEIGHT), FRAME_HEIGHT, FRAME_WIDTH)
