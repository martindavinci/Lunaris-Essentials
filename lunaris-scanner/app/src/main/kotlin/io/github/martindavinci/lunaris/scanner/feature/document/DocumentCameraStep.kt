package io.github.martindavinci.lunaris.scanner.feature.document

import androidx.camera.core.Camera
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.feature.scan.CameraAccess
import io.github.martindavinci.lunaris.scanner.feature.scan.CameraGate
import io.github.martindavinci.lunaris.scanner.feature.scan.TorchAction
import io.github.martindavinci.lunaris.scanner.ui.theme.LunarisScannerTheme

private val BarMinHeight = 56.dp
private val PillPadding = 12.dp
private val PillVerticalPadding = 6.dp
private val PickPadding = 16.dp
private val PillSpacing = 8.dp

/** Page after page: the shutter, then the corners, and back here. The bars are drawn over a preview that may run past its bounds. */
@Composable
internal fun DocumentCameraStep(
    state: DocumentUiState,
    onAction: (DocumentAction) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickImage: () -> Unit,
) {
    val context = LocalContext.current
    val capture = remember(context) { DocumentCapture(context) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val cameraRuns = state.cameraAccess == CameraAccess.GRANTED && !state.cameraUnavailable
    val stage = @Composable { modifier: Modifier ->
        Box(modifier) {
            CameraGate(state.cameraAccess, state.cameraUnavailable, onRequestPermission, onOpenSettings) {
                DocumentCameraPanel(
                    capture = capture,
                    onUnavailable = { onAction(DocumentAction.CameraUnavailable) },
                    onCamera = { camera = it },
                )
            }
            // Without a camera this is all that is left, and picking a photo needs no permission.
            if (!cameraRuns) {
                FilledTonalButton(
                    onClick = onPickImage,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(PickPadding),
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null)
                    Spacer(Modifier.width(PillSpacing))
                    Text(stringResource(R.string.document_pick_image))
                }
            }
        }
    }
    val controls = @Composable { sideways: Boolean ->
        CameraControls(
            pages = state.pages,
            shutterEnabled = cameraRuns && capture.ready,
            sideways = sideways,
            onShutter = {
                capture.take(
                    onTaken = { onAction(DocumentAction.PhotoTaken(it)) },
                    onFailed = { onAction(DocumentAction.CameraFailed) },
                )
            },
            onFinish = { onAction(DocumentAction.FinishPages) },
        )
    }
    val topBar = @Composable {
        CameraTopBar(state.pages.size + 1, cameraRuns, camera, onPickImage) { onAction(DocumentAction.BackPressed) }
    }
    // On a wide screen the controls go down the side, or they would leave the preview a strip.
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    topBar()
                    stage(Modifier.weight(1f).fillMaxWidth())
                }
                controls(true)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                topBar()
                stage(Modifier.weight(1f).fillMaxWidth())
                controls(false)
            }
        }
    }
}

@Composable
private fun CameraTopBar(pageNumber: Int, cameraRuns: Boolean, camera: Camera?, onPickImage: () -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .zIndex(1f)
            .background(MaterialTheme.colorScheme.background)
            .fillMaxWidth()
            .heightIn(min = BarMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                text = stringResource(R.string.document_page_number, pageNumber),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = PillPadding, vertical = PillVerticalPadding),
            )
        }
        Spacer(Modifier.weight(1f))
        if (cameraRuns) {
            IconButton(onClick = onPickImage) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = stringResource(R.string.document_pick_image))
            }
            TorchAction(camera)
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun DocumentCameraStepPreview() {
    LunarisScannerTheme {
        DocumentCameraStep(
            state = DocumentUiState(cameraAccess = CameraAccess.PERMANENTLY_DENIED),
            onAction = {},
            onRequestPermission = {},
            onOpenSettings = {},
            onPickImage = {},
        )
    }
}
