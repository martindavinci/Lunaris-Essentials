package io.github.martindavinci.lunaris.scanner.feature.scan

import android.Manifest
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.martindavinci.lunaris.scanner.feature.result.ResultActionRunner

/** [request] comes from outside the screen. A document scan is a screen of its own, opened by [onDocumentChosen]; [onOpenResult] shows a code in full. */
@Composable
fun ScanRoute(request: ScanRequest?, onOpenResult: () -> Unit, onDocumentChosen: () -> Unit) {
    val viewModel: ScanViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    HitEffect(viewModel)
    LaunchedEffect(request) { request?.let { viewModel.onAction(ScanAction.Requested(it)) } }

    DisposableEffect(viewModel) {
        onDispose {
            if (context.findActivity()?.isChangingConfigurations != true) viewModel.onAction(ScanAction.ScreenLeft)
        }
    }
    LifecycleStartEffect(viewModel) {
        onStopOrDispose {
            if (context.findActivity()?.isChangingConfigurations != true) viewModel.onAction(ScanAction.ScreenHidden)
        }
    }

    // Frames count only while this screen is in front, not while a picked image is being read, and not during a pause.
    LifecycleResumeEffect(viewModel.analyzer, state.decodingImage, state.readingPaused) {
        if (!state.decodingImage && !state.readingPaused) viewModel.analyzer.resume()
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
        viewModel.onAction(ScanAction.ScreenShown)
        viewModel.onAction(context.cameraPermissionState())
        onPauseOrDispose {}
    }
    // Only once the camera is wanted: the choice itself needs no permission.
    LaunchedEffect(state.cameraAccess, state.choosing) {
        if (!state.choosing && state.cameraAccess == CameraAccess.NEEDS_REQUEST) requestPermission()
    }

    if (state.choosing) {
        ScanChoiceScreen(onCode = { viewModel.onAction(ScanAction.CodeChosen) }, onDocument = onDocumentChosen)
        return
    }

    BackHandler { viewModel.onAction(ScanAction.ChoiceAsked) }
    BackHandler(enabled = state.listOpen) { viewModel.onAction(ScanAction.ListClosed) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) viewModel.onAction(ScanAction.ImagePicked(uri.toString()))
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    // Nothing from a card needs a result back: that is for joining a Wi-Fi network, on the full page.
    val runner = remember(context) { ResultActionRunner(context) {} }
    ScanScreen(
        state = state,
        onBack = { viewModel.onAction(ScanAction.ChoiceAsked) },
        onRequestPermission = requestPermission,
        onOpenSettings = context::openAppSettings,
        onPickImage = {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onNoCodeMessageShown = { viewModel.onAction(ScanAction.NoCodeMessageShown) },
        onAction = viewModel::onAction,
        onDetails = {
            viewModel.onAction(ScanAction.DetailsAsked(it))
            onOpenResult()
        },
        onRun = runner::run,
        torch = { TorchAction(camera) },
        camera = {
            CameraPanel(
                analyzer = viewModel.analyzer,
                onUnavailable = { viewModel.onAction(ScanAction.CameraUnavailable) },
                onCamera = { camera = it },
            )
        },
    )
}

@Composable
private fun HitEffect(viewModel: ScanViewModel) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(viewModel) {
        viewModel.hits.collect { hit ->
            if (hit.vibrate) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}
