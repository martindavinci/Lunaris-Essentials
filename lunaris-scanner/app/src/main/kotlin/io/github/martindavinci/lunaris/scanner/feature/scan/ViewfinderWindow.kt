package io.github.martindavinci.lunaris.scanner.feature.scan

import kotlin.math.min

private const val WINDOW_WIDTH_SHARE = 0.9f
private const val WINDOW_HEIGHT_SHARE_OF_SHORT_SIDE = 0.7f

/**
 * The scanning window on screen: what it shows is all the camera reads. Wider than high, so a long
 * barcode fits and a code above or below stays out.
 */
internal data class ViewfinderWindow(val width: Float, val height: Float) {
    companion object {
        fun of(viewWidth: Float, viewHeight: Float) = ViewfinderWindow(
            width = viewWidth * WINDOW_WIDTH_SHARE,
            height = min(viewWidth, viewHeight) * WINDOW_HEIGHT_SHARE_OF_SHORT_SIDE,
        )
    }
}
