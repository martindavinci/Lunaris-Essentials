package io.github.martindavinci.lunaris.scanner.feature.scan

import android.content.Context
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.InitializationException
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.scan.CameraAnalyzer
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** Dense QR codes and long barcodes need the pixels; larger frames only make every failed attempt slower. */
private const val ANALYSIS_WIDTH_PX = 1920
private const val ANALYSIS_HEIGHT_PX = 1080

/** The live camera with its gestures and controls. The camera runs for as long as this is composed. */
@Composable
internal fun CameraPanel(analyzer: CameraAnalyzer, onUnavailable: () -> Unit, onCamera: (Camera?) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        // TextureView: a SurfaceView would not follow the navigation transitions.
        PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    val currentOnCamera by rememberUpdatedState(onCamera)

    LaunchedEffect(lifecycleOwner, analyzer) {
        try {
            runCamera(context, lifecycleOwner, previewView, analyzer) {
                camera = it
                currentOnCamera(it)
            }
        } catch (_: InitializationException) {
            currentOnUnavailable()
        } catch (_: IllegalArgumentException) {
            currentOnUnavailable()
        }
    }

    // Clipped: to fill the panel the preview scales past its bounds, and would show over the status bar.
    Box(
        Modifier.fillMaxSize().clipToBounds().onSizeChanged { size ->
            val window = ViewfinderWindow.of(size.width.toFloat(), size.height.toFloat())
            analyzer.setWindow(window.width / size.width, window.height / size.height)
        },
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        camera?.let { Box(Modifier.fillMaxSize().cameraGestures(it, previewView)) }
        ViewfinderOverlay(Modifier.fillMaxSize())
    }
}

/** Binds the camera, keeps it bound until cancelled, then unbinds. Throws if there is no usable camera. */
private suspend fun runCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    analyzer: CameraAnalyzer,
    onCamera: (Camera?) -> Unit,
) {
    val provider = ProcessCameraProvider.awaitInstance(context)
    val preview = Preview.Builder().build()
    preview.setSurfaceProvider(previewView.surfaceProvider)
    val analysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setResolutionSelector(analysisResolution())
        .build()
    val executor = Executors.newSingleThreadExecutor()
    analysis.setAnalyzer(executor, analyzer)
    previewView.awaitLayout()
    // The view port makes each frame's crop rect the part the preview shows, which the analyzer relies on.
    val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(analysis)
    previewView.viewPort?.let(group::setViewPort)
    try {
        onCamera(provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group.build()))
        awaitCancellation()
    } finally {
        onCamera(null)
        analysis.clearAnalyzer()
        provider.unbind(preview, analysis)
        executor.shutdown()
    }
}

internal suspend fun PreviewView.awaitLayout() = suspendCancellableCoroutine { continuation ->
    doOnLayout { continuation.resume(Unit) }
}

private fun analysisResolution() = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(
        ResolutionStrategy(
            Size(ANALYSIS_WIDTH_PX, ANALYSIS_HEIGHT_PX),
            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
        ),
    )
    .build()

internal fun Modifier.cameraGestures(camera: Camera, previewView: PreviewView) = this
    .pointerInput(camera) { detectTransformGestures { _, _, zoom, _ -> camera.zoomBy(zoom) } }
    .pointerInput(camera) { detectTapGestures { camera.focusAt(previewView, it) } }

private fun Camera.zoomBy(factor: Float) {
    val zoom = cameraInfo.zoomState.value ?: return
    cameraControl.setZoomRatio((zoom.zoomRatio * factor).coerceIn(zoom.minZoomRatio, zoom.maxZoomRatio))
}

private fun Camera.focusAt(previewView: PreviewView, position: Offset) {
    val point = previewView.meteringPointFactory.createPoint(position.x, position.y)
    cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
}

/** The button for a bar, when the camera is running and has a torch at all. */
@Composable
internal fun TorchAction(camera: Camera?) {
    camera?.takeIf { it.cameraInfo.hasFlashUnit() }?.let { TorchButton(it, LocalLifecycleOwner.current) }
}

/** Shows what the camera reports: the torch also goes off by itself when the camera stops. */
@Composable
private fun TorchButton(camera: Camera, lifecycleOwner: LifecycleOwner) {
    var torchOn by remember(camera) { mutableStateOf(false) }
    DisposableEffect(camera, lifecycleOwner) {
        val observer = Observer<Int> { torchOn = it == TorchState.ON }
        camera.cameraInfo.torchState.observe(lifecycleOwner, observer)
        onDispose { camera.cameraInfo.torchState.removeObserver(observer) }
    }
    IconButton(onClick = { camera.cameraControl.enableTorch(!torchOn) }) {
        Icon(
            imageVector = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
            contentDescription = stringResource(if (torchOn) R.string.scan_torch_off else R.string.scan_torch_on),
        )
    }
}
