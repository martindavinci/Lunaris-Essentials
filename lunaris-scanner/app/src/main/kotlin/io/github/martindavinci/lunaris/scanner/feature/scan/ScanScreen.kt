package io.github.martindavinci.lunaris.scanner.feature.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.feature.ScreenTopBar
import io.github.martindavinci.lunaris.scanner.ui.theme.LunarisScannerTheme

private val ControlsPadding = 16.dp
private val ControlsSpacing = 8.dp
private val ButtonIconSpacing = 8.dp

/** Stateless: [camera] is the live preview and [torch] its button, which only the route can provide. */
@Composable
internal fun ScanScreen(
    state: ScanUiState,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickImage: () -> Unit,
    onNoCodeMessageShown: () -> Unit,
    torch: @Composable () -> Unit,
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
    val cameraRuns = state.cameraAccess == CameraAccess.GRANTED && !state.cameraUnavailable
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenTopBar(
            title = stringResource(R.string.scan_code_title),
            onBack = onBack,
            actions = {
                if (cameraRuns) {
                    IconButton(onClick = onPickImage, enabled = !state.decodingImage) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = stringResource(R.string.scan_pick_image))
                    }
                    torch()
                }
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CameraGate(state.cameraAccess, state.cameraUnavailable, onRequestPermission, onOpenSettings, camera)
            if (state.decodingImage) CircularProgressIndicator(Modifier.align(Alignment.Center))
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(ControlsPadding),
                verticalArrangement = Arrangement.spacedBy(ControlsSpacing),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SnackbarHost(snackbarHost)
                // Without a camera this is all that is left, and reading a picture needs no permission.
                if (!cameraRuns) {
                    FilledTonalButton(onClick = onPickImage, enabled = !state.decodingImage) {
                        Icon(Icons.Filled.Image, contentDescription = null)
                        Spacer(Modifier.width(ButtonIconSpacing))
                        Text(stringResource(R.string.scan_pick_image))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ScanScreenCameraPreview() {
    LunarisScannerTheme {
        ScanScreen(
            state = ScanUiState(choosing = false, cameraAccess = CameraAccess.GRANTED),
            onBack = {},
            onRequestPermission = {},
            onOpenSettings = {},
            onPickImage = {},
            onNoCodeMessageShown = {},
            torch = {},
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
            state = ScanUiState(choosing = false, cameraAccess = CameraAccess.PERMANENTLY_DENIED),
            onBack = {},
            onRequestPermission = {},
            onOpenSettings = {},
            onPickImage = {},
            onNoCodeMessageShown = {},
            torch = {},
            camera = {},
        )
    }
}
