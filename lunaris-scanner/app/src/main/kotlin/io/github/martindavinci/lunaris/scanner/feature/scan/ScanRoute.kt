package io.github.martindavinci.lunaris.scanner.feature.scan

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ScanRoute(onCodeScanned: () -> Unit) {
    val viewModel: ScanViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    HitEffect(viewModel, onCodeScanned)

    DisposableEffect(viewModel) {
        onDispose {
            if (context.findActivity()?.isChangingConfigurations != true) viewModel.onAction(ScanAction.ScreenLeft)
        }
    }

    // Frames count only while this screen is in front, and not while a picked image is being read.
    LifecycleResumeEffect(viewModel.analyzer, state.decodingImage) {
        if (!state.decodingImage) viewModel.analyzer.resume()
        onPauseOrDispose { viewModel.analyzer.pause() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onAction(context.cameraPermissionState())
    }
    val requestPermission = {
        viewModel.onAction(ScanAction.PermissionRequested)
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    // On every return to the screen: the user may have changed the permission in system settings.
    LifecycleResumeEffect(Unit) {
        viewModel.onAction(context.cameraPermissionState())
        onPauseOrDispose {}
    }
    LaunchedEffect(state.cameraAccess) {
        if (state.cameraAccess == CameraAccess.NEEDS_REQUEST) requestPermission()
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) viewModel.onAction(ScanAction.ImagePicked(uri.toString()))
    }

    ScanScreen(
        state = state,
        onRequestPermission = requestPermission,
        onOpenSettings = context::openAppSettings,
        onPickImage = {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onNoCodeMessageShown = { viewModel.onAction(ScanAction.NoCodeMessageShown) },
        camera = {
            CameraPanel(
                analyzer = viewModel.analyzer,
                onUnavailable = { viewModel.onAction(ScanAction.CameraUnavailable) },
            )
        },
    )
}

@Composable
private fun HitEffect(viewModel: ScanViewModel, onCodeScanned: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnCodeScanned by rememberUpdatedState(onCodeScanned)
    LaunchedEffect(viewModel) {
        viewModel.hits.collect { hit ->
            if (hit.vibrate) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            currentOnCodeScanned()
        }
    }
}
