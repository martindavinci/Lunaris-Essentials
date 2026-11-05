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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.document.PictureFormat
import io.github.martindavinci.lunaris.scanner.document.scanFileName
import io.github.martindavinci.lunaris.scanner.feature.scan.cameraPermissionState
import java.time.LocalDateTime

private const val PDF_MIME_TYPE = "application/pdf"
private const val PNG_MIME_TYPE = "image/png"
private const val JPEG_MIME_TYPE = "image/jpeg"
private const val PDF_EXTENSION = "pdf"
private const val PNG_EXTENSION = "png"
private const val JPEG_EXTENSION = "jpg"
private const val BUSY_SCRIM_ALPHA = 0.4f

/** A scan under way, from its first photo to the saved file. The steps share one screen and one back stack entry. */
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
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onAction(if (granted) DocumentAction.OpenCamera else DocumentAction.CameraRefused)
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME_TYPE)) { uri ->
        if (uri != null) viewModel.onAction(DocumentAction.SavePdfTo(uri.toString()))
    }
    val savePng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PNG_MIME_TYPE)) { uri ->
        if (uri != null) viewModel.onAction(DocumentAction.SavePictureTo(uri.toString(), PictureFormat.PNG))
    }
    val saveJpeg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JPEG_MIME_TYPE)) { uri ->
        if (uri != null) viewModel.onAction(DocumentAction.SavePictureTo(uri.toString(), PictureFormat.JPEG))
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (state.step) {
            DocumentStep.CAMERA -> DocumentCameraStep(viewModel::onAction)
            DocumentStep.CORNERS -> CornersScreen(state, viewModel::onAction)
            DocumentStep.REVIEW -> if (state.currentPage != null) {
                ReviewScreen(
                    state = state,
                    onAction = viewModel::onAction,
                    onPickImage = {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onTakePhoto = {
                        if (context.cameraPermissionState().granted) {
                            viewModel.onAction(DocumentAction.OpenCamera)
                        } else {
                            permission.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onSavePdf = { savePdf.launch(scanFileName(PDF_EXTENSION, LocalDateTime.now())) },
                    onSavePng = { savePng.launch(scanFileName(PNG_EXTENSION, LocalDateTime.now())) },
                    onSaveJpeg = { saveJpeg.launch(scanFileName(JPEG_EXTENSION, LocalDateTime.now())) },
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

@Composable
private fun ConfirmDialog(confirm: DocumentConfirm, onAction: (DocumentAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(DocumentAction.Dismissed) },
        title = {
            Text(
                stringResource(
                    when (confirm) {
                        DocumentConfirm.DELETE_PAGE -> R.string.document_delete_title
                        DocumentConfirm.DISCARD -> R.string.document_discard_title
                    },
                ),
            )
        },
        text = if (confirm == DocumentConfirm.DISCARD) {
            { Text(stringResource(R.string.document_discard_message)) }
        } else {
            null
        },
        confirmButton = {
            TextButton(onClick = { onAction(DocumentAction.Confirmed) }) {
                Text(
                    stringResource(
                        when (confirm) {
                            DocumentConfirm.DELETE_PAGE -> R.string.delete
                            DocumentConfirm.DISCARD -> R.string.document_discard
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(DocumentAction.Dismissed) }) { Text(stringResource(R.string.cancel)) }
        },
    )
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
