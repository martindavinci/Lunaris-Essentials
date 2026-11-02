package io.github.martindavinci.lunaris.scanner.feature.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.ui.theme.LunarisScannerTheme

private val ControlsPadding = 16.dp
private val ControlsSpacing = 8.dp
private val PanelPadding = 32.dp
private val PanelSpacing = 16.dp
private val PanelIconSize = 48.dp
private val PanelBottomClearance = 96.dp
private val ButtonIconSpacing = 8.dp

/** Stateless: [camera] is the live preview, which only the route can provide. */
@Composable
internal fun ScanScreen(
    state: ScanUiState,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickImage: () -> Unit,
    onNoCodeMessageShown: () -> Unit,
    camera: @Composable () -> Unit,
) {
    val snackbarHost = remember { SnackbarHostState() }
    val noCodeMessage = stringResource(R.string.scan_no_code_found)
    LaunchedEffect(state.noCodeFound) {
        if (state.noCodeFound) {
            snackbarHost.showSnackbar(noCodeMessage)
            onNoCodeMessageShown()
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            state.cameraAccess == CameraAccess.RATIONALE -> CameraMessage(
                message = stringResource(R.string.scan_permission_rationale),
                actionLabel = stringResource(R.string.scan_permission_allow),
                onAction = onRequestPermission,
            )
            state.cameraAccess == CameraAccess.PERMANENTLY_DENIED -> CameraMessage(
                message = stringResource(R.string.scan_permission_denied),
                actionLabel = stringResource(R.string.scan_permission_settings),
                onAction = onOpenSettings,
                // Dismissing the system dialog looks just like a final refusal, so asking again stays open.
                secondaryActionLabel = stringResource(R.string.scan_permission_allow),
                onSecondaryAction = onRequestPermission,
            )
            state.cameraAccess != CameraAccess.GRANTED -> Unit
            state.cameraUnavailable -> CameraMessage(stringResource(R.string.scan_camera_unavailable))
            else -> camera()
        }
        if (state.decodingImage) CircularProgressIndicator(Modifier.align(Alignment.Center))
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(ControlsPadding),
            verticalArrangement = Arrangement.spacedBy(ControlsSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SnackbarHost(snackbarHost)
            // Always here: reading a picture needs no permission and no camera.
            FilledTonalButton(onClick = onPickImage, enabled = !state.decodingImage) {
                Icon(Icons.Filled.Image, contentDescription = null)
                Spacer(Modifier.width(ButtonIconSpacing))
                Text(stringResource(R.string.scan_pick_image))
            }
        }
    }
}

/** Takes the place of the camera when it cannot run, with room left for the controls below it. */
@Composable
private fun CameraMessage(
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    secondaryActionLabel: String? = null,
    onSecondaryAction: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(
            start = PanelPadding,
            end = PanelPadding,
            top = PanelPadding,
            bottom = PanelBottomClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(PanelSpacing, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.NoPhotography,
            contentDescription = null,
            modifier = Modifier.size(PanelIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) Button(onClick = onAction) { Text(actionLabel) }
        if (secondaryActionLabel != null) OutlinedButton(onClick = onSecondaryAction) { Text(secondaryActionLabel) }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ScanScreenCameraPreview() {
    LunarisScannerTheme {
        ScanScreen(
            state = ScanUiState(cameraAccess = CameraAccess.GRANTED),
            onRequestPermission = {},
            onOpenSettings = {},
            onPickImage = {},
            onNoCodeMessageShown = {},
            camera = {
                Box(Modifier.fillMaxSize().background(Color.DarkGray))
                ViewfinderOverlay(Modifier.fillMaxSize())
            },
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ScanScreenDeniedPreview() {
    LunarisScannerTheme {
        ScanScreen(
            state = ScanUiState(cameraAccess = CameraAccess.PERMANENTLY_DENIED),
            onRequestPermission = {},
            onOpenSettings = {},
            onPickImage = {},
            onNoCodeMessageShown = {},
            camera = {},
        )
    }
}
