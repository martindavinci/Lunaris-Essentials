package io.github.martindavinci.lunaris.scanner.feature.document

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.feature.scan.DocumentCameraPanel
import io.github.martindavinci.lunaris.scanner.feature.scan.DocumentCapture
import io.github.martindavinci.lunaris.scanner.feature.scan.ShutterButton

private val ShutterPadding = 24.dp

/** Taking another page of the scan under way. */
@Composable
internal fun DocumentCameraStep(onAction: (DocumentAction) -> Unit) {
    val context = LocalContext.current
    val capture = remember(context) { DocumentCapture(context) }
    Column(Modifier.fillMaxSize()) {
        DocumentTopBar(stringResource(R.string.document_add_page), onBack = { onAction(DocumentAction.BackPressed) })
        Box(Modifier.weight(1f)) {
            DocumentCameraPanel(capture, onUnavailable = { onAction(DocumentAction.CameraFailed) })
            ShutterButton(
                enabled = capture.ready,
                onClick = {
                    capture.take(
                        onTaken = { onAction(DocumentAction.PhotoTaken(it)) },
                        onFailed = { onAction(DocumentAction.CameraFailed) },
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(ShutterPadding),
            )
        }
    }
}
