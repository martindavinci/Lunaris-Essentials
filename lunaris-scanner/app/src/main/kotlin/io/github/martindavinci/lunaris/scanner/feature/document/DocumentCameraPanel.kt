package io.github.martindavinci.lunaris.scanner.feature.document

import android.content.Context
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.InitializationException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.document.newPhotoFile
import io.github.martindavinci.lunaris.scanner.feature.scan.awaitLayout
import io.github.martindavinci.lunaris.scanner.feature.scan.cameraGestures
import kotlinx.coroutines.awaitCancellation

/** Enough for a page at 300 dots per inch. A larger photo would only be scaled down afterwards. */
private const val PHOTO_WIDTH_PX = 4000
private const val PHOTO_HEIGHT_PX = 3000

private val HintPadding = 12.dp
private val HintInnerPadding = 8.dp
private const val HINT_ALPHA = 0.85f

/** Takes the photos of the panel it is given to. */
@Stable
internal class DocumentCapture(private val context: Context) {
    private var useCase by mutableStateOf<ImageCapture?>(null)
    private var taking by mutableStateOf(false)

    val ready: Boolean get() = useCase != null && !taking

    fun attach(capture: ImageCapture?) {
        useCase = capture
    }

    fun take(onTaken: (String) -> Unit, onFailed: () -> Unit) {
        val capture = useCase ?: return
        val file = newPhotoFile(context.cacheDir)
        taking = true
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    taking = false
                    onTaken(file.path)
                }

                override fun onError(exception: ImageCaptureException) {
                    taking = false
                    file.delete()
                    onFailed()
                }
            },
        )
    }
}

/** The camera for pages: the whole frame in view, since whatever it sees ends up in the photo. */
@Composable
internal fun DocumentCameraPanel(capture: DocumentCapture, onUnavailable: () -> Unit, onCamera: (Camera?) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    val currentOnCamera by rememberUpdatedState(onCamera)

    LaunchedEffect(lifecycleOwner, capture) {
        try {
            runDocumentCamera(context, lifecycleOwner, previewView, capture) {
                camera = it
                currentOnCamera(it)
            }
        } catch (_: InitializationException) {
            currentOnUnavailable()
        } catch (_: IllegalArgumentException) {
            currentOnUnavailable()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        camera?.let { Box(Modifier.fillMaxSize().cameraGestures(it, previewView)) }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(HintPadding),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface.copy(alpha = HINT_ALPHA),
        ) {
            Text(
                text = stringResource(R.string.document_camera_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(HintInnerPadding),
            )
        }
    }
}

private suspend fun runDocumentCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    capture: DocumentCapture,
    onCamera: (Camera?) -> Unit,
) {
    val provider = ProcessCameraProvider.awaitInstance(context)
    val fourByThree = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
        .build()
    val preview = Preview.Builder().setResolutionSelector(fourByThree).build()
    preview.setSurfaceProvider(previewView.surfaceProvider)
    previewView.awaitLayout()
    val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .setResolutionSelector(photoResolution())
        .setTargetRotation(previewView.display.rotation)
        .build()
    try {
        onCamera(provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture))
        capture.attach(imageCapture)
        awaitCancellation()
    } finally {
        capture.attach(null)
        onCamera(null)
        provider.unbind(preview, imageCapture)
    }
}

private fun photoResolution() = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(
        ResolutionStrategy(
            Size(PHOTO_WIDTH_PX, PHOTO_HEIGHT_PX),
            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
        ),
    )
    .build()
