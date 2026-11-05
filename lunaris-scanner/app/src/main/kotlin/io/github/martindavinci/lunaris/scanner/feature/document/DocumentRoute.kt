package io.github.martindavinci.lunaris.scanner.feature.document

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.document.PictureFormat
import io.github.martindavinci.lunaris.scanner.document.scanFileName
import io.github.martindavinci.lunaris.scanner.feature.scan.CameraAccess
import io.github.martindavinci.lunaris.scanner.feature.scan.cameraPermissionState
import io.github.martindavinci.lunaris.scanner.feature.scan.findActivity
import io.github.martindavinci.lunaris.scanner.feature.scan.openAppSettings
import java.time.LocalDateTime

private const val PDF_MIME_TYPE = "application/pdf"
private const val PDF_EXTENSION = "pdf"
private const val BUSY_SCRIM_ALPHA = 0.4f

/** A scan under way, from the camera to the saved file. The steps share one screen and one back stack entry. */
@Composable
fun DocumentRoute(onClose: () -> Unit) {
    val viewModel: DocumentViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val currentOnClose by rememberUpdatedState(onClose)

    LaunchedEffect(state.closed) { if (state.closed) currentOnClose() }
    BackHandler(enabled = !state.closed) { viewModel.onAction(DocumentAction.BackPressed) }

    val message = state.message?.let { stringResource(messageOf(it)) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message, duration = SnackbarDuration.Short)
            viewModel.onAction(DocumentAction.MessageShown)
        }
    }
    LaunchedEffect(state.shareUri) {
        state.shareUri?.let {
            sharePdf(context, it)
            viewModel.onAction(DocumentAction.ShareHandled)
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onAction(DocumentAction.PhotoPicked(uri.toString()))
    }
    val checkPermission = {
        val now = context.cameraPermissionState()
        viewModel.onAction(DocumentAction.CameraPermissionChecked(now.granted, now.showRationale))
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { checkPermission() }
    val requestPermission = {
        viewModel.onAction(DocumentAction.CameraPermissionRequested)
        permission.launch(Manifest.permission.CAMERA)
    }
    LifecycleStartEffect(viewModel) {
        onStopOrDispose {
            if (context.findActivity()?.isChangingConfigurations != true) viewModel.onAction(DocumentAction.ScreenHidden)
        }
    }
    // On every return to the screen: the user may have changed the permission in system settings.
    LifecycleResumeEffect(Unit) {
        viewModel.onAction(DocumentAction.ScreenShown)
        checkPermission()
        onPauseOrDispose {}
    }
    LaunchedEffect(state.cameraAccess, state.step) {
        if (state.step == DocumentStep.CAMERA && state.cameraAccess == CameraAccess.NEEDS_REQUEST) requestPermission()
    }
    val pickImage = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME_TYPE)) { uri ->
        if (uri != null) viewModel.onAction(DocumentAction.SavePdfTo(uri.toString()))
    }
    val savePng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PictureFormat.PNG.mimeType)) { uri ->
        if (uri != null) viewModel.onAction(DocumentAction.SavePictureTo(uri.toString(), PictureFormat.PNG))
    }
    val saveJpeg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PictureFormat.JPEG.mimeType)) { uri ->
        if (uri != null) viewModel.onAction(DocumentAction.SavePictureTo(uri.toString(), PictureFormat.JPEG))
    }
    var folderFormat by rememberSaveable { mutableStateOf(PictureFormat.JPEG) }
    val saveToFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.onAction(DocumentAction.SavePicturesTo(uri.toString(), folderFormat))
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (state.step) {
            DocumentStep.CAMERA -> DocumentCameraStep(state, viewModel::onAction, requestPermission, context::openAppSettings, pickImage)
            DocumentStep.CORNERS -> CornersScreen(state, viewModel::onAction)
            DocumentStep.REVIEW -> if (state.currentPage != null) {
                ReviewScreen(
                    state = state,
                    onAction = viewModel::onAction,
                    onPickImage = pickImage,
                    onTakePhoto = { viewModel.onAction(DocumentAction.OpenCamera) },
                    onSavePdf = { savePdf.launch(scanFileName(PDF_EXTENSION, LocalDateTime.now())) },
                    onSavePicture = { format ->
                        when {
                            state.pages.size > 1 -> {
                                folderFormat = format
                                saveToFolder.launch(null)
                            }
                            format == PictureFormat.PNG -> savePng.launch(scanFileName(format.extension, LocalDateTime.now()))
                            else -> saveJpeg.launch(scanFileName(format.extension, LocalDateTime.now()))
                        }
                    },
                )
            }
        }
        if (state.busy) BusyCover()
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    state.confirm?.let { ConfirmDialog(it, viewModel::onAction) }
}

/** While a file is being written the pages must stay as they are. */
@Composable
private fun BusyCover() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = BUSY_SCRIM_ALPHA))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

private fun messageOf(message: DocumentMessage): Int = when (message) {
    DocumentMessage.PHOTO_FAILED -> R.string.document_read_failed
    DocumentMessage.CAMERA_FAILED -> R.string.document_camera_failed
    DocumentMessage.PAGE_FAILED -> R.string.document_page_failed
    DocumentMessage.SAVED -> R.string.document_saved
    DocumentMessage.SAVE_FAILED -> R.string.document_save_failed
    DocumentMessage.SHARE_FAILED -> R.string.document_share_failed
}

private fun sharePdf(context: Context, uri: String) {
    val stream = uri.toUri()
    val send = Intent(Intent.ACTION_SEND).apply {
        type = PDF_MIME_TYPE
        putExtra(Intent.EXTRA_STREAM, stream)
        clipData = ClipData.newRawUri(null, stream)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}
