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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ScanRoute(onCodeScanned: () -> Unit, onDocumentStarted: () -> Unit) {
    val viewModel: ScanViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val capture = remember(context) { DocumentCapture(context) }

    HitEffect(viewModel, onCodeScanned)
    val currentOnDocumentStarted by rememberUpdatedState(onDocumentStarted)
    LaunchedEffect(viewModel) { viewModel.documents.collect { currentOnDocumentStarted() } }

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
        if (uri == null) return@rememberLauncherForActivityResult
        val text = uri.toString()
        viewModel.onAction(if (viewModel.state.value.mode == ScanMode.CODE) ScanAction.ImagePicked(text) else ScanAction.DocumentImagePicked(text))
    }

    ScanScreen(
        state = state,
        onRequestPermission = requestPermission,
        onOpenSettings = context::openAppSettings,
        onPickImage = {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onNoCodeMessageShown = { viewModel.onAction(ScanAction.NoCodeMessageShown) },
        onAction = viewModel::onAction,
        shutterReady = capture.ready,
        onShutter = {
            viewModel.onAction(ScanAction.ShutterPressed)
            capture.take(
                onTaken = { viewModel.onAction(ScanAction.DocumentPhotoTaken(it)) },
                onFailed = { viewModel.onAction(ScanAction.DocumentPhotoFailed) },
            )
        },
        camera = {
            val onUnavailable = { viewModel.onAction(ScanAction.CameraUnavailable) }
            if (state.mode == ScanMode.CODE) {
                CameraPanel(analyzer = viewModel.analyzer, onUnavailable = onUnavailable)
            } else {
                DocumentCameraPanel(capture, onUnavailable = onUnavailable)
            }
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
