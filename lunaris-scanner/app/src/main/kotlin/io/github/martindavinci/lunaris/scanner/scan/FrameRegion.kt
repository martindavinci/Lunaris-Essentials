package io.github.martindavinci.lunaris.scanner.scan

import kotlin.math.min

private const val HALF_TURN_DEGREES = 180

/** Lets a code that just fills the window keep the light margin it needs around it. */
private const val WINDOW_MARGIN_SHARE = 0.06f

/** A rectangle of a camera frame, in the frame's own pixels. */
internal data class FrameRegion(val left: Int, val top: Int, val width: Int, val height: Int) {
    /**
     * The part of this visible region behind the centred viewfinder window. The shares are the
     * window's size against the screen's; camera frames are often sideways to the screen.
     */
    fun behindWindow(rotationDegrees: Int, widthShare: Float, heightShare: Float): FrameRegion {
        val sideways = rotationDegrees % HALF_TURN_DEGREES != 0
        val outWidth = scaled(width, if (sideways) heightShare else widthShare)
        val outHeight = scaled(height, if (sideways) widthShare else heightShare)
        return FrameRegion(left + (width - outWidth) / 2, top + (height - outHeight) / 2, outWidth, outHeight)
    }

    private fun scaled(side: Int, share: Float) = (side * min(1f, share + WINDOW_MARGIN_SHARE)).toInt()
}
