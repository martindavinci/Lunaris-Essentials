package io.github.martindavinci.lunaris.scanner.scan

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Looks for a code in the camera frames. It starts paused; after a hit it calls [onCode] once, on
 * the analysis thread, and pauses again until [resume].
 */
class CameraAnalyzer(
    private val decoder: CodeDecoder,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val onCode: (ScannedCode) -> Unit,
) : ImageAnalysis.Analyzer {
    private val paused = AtomicBoolean(true)
    private val agreement = ReadAgreement()
    private val repeatGuard = RepeatGuard()

    @Volatile private var windowWidthShare = 1f

    @Volatile private var windowHeightShare = 1f

    override fun analyze(image: ImageProxy) {
        image.use {
            if (paused.get()) return
            val plane = image.planes[0]
            val rotation = image.imageInfo.rotationDegrees
            val visible = image.cropRect.run { FrameRegion(left, top, width(), height()) }
            val region = visible.behindWindow(rotation, windowWidthShare, windowHeightShare)
            val luminance =
                copyLuminance(plane.buffer, plane.rowStride, region.width, region.height, region.left, region.top)
            analyzeFrame(luminance, region.width, region.height, rotation)
        }
    }

    /** Only what the viewfinder window shows is read: the window's size against the screen's. */
    fun setWindow(widthShare: Float, heightShare: Float) {
        windowWidthShare = widthShare
        windowHeightShare = heightShare
    }

    fun resume() {
        agreement.reset()
        repeatGuard.rearm(clock())
        paused.set(false)
    }

    fun pause() {
        paused.set(true)
    }

    /**
     * ZXing reads barcodes only along the pixel rows and camera sensors are mounted sideways, so a
     * barcode level on a portrait screen runs across the rows. The frame is tried as the user sees
     * it first, then the other way round.
     */
    internal fun analyzeFrame(luminance: ByteArray, width: Int, height: Int, rotationDegrees: Int) {
        if (paused.get()) return
        val asIs = { decoder.decode(luminance, width, height) }
        val turned = { decoder.decode(rotate90(luminance, width, height), height, width) }
        val code = if (rotationDegrees % HALF_TURN_DEGREES == 0) asIs() ?: turned() else turned() ?: asIs()
        if (code != null) deliver(code)
    }

    /** Paused during the decode means nobody will see the code, so the guard must not remember it. */
    internal fun deliver(code: ScannedCode) {
        if (paused.get() || !agreement.accept(code, clock())) return
        if (repeatGuard.accept(code, clock()) && paused.compareAndSet(false, true)) {
            onCode(code)
        }
    }

    private companion object {
        const val HALF_TURN_DEGREES = 180
    }
}
