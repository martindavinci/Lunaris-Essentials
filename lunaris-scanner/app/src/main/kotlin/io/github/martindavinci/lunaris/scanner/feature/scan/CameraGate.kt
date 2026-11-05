package io.github.martindavinci.lunaris.scanner.feature.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.scanner.R

private val PanelPadding = 32.dp
private val PanelSpacing = 16.dp
private val PanelIconSize = 48.dp
private val PanelBottomClearance = 96.dp

/** The camera, once it may run. Otherwise, why it cannot and what to do about it. */
@Composable
internal fun CameraGate(
    access: CameraAccess,
    unavailable: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    camera: @Composable () -> Unit,
) {
    when {
        access == CameraAccess.RATIONALE -> CameraMessage(
            message = stringResource(R.string.scan_permission_rationale),
            actionLabel = stringResource(R.string.scan_permission_allow),
            onAction = onRequestPermission,
        )
        access == CameraAccess.PERMANENTLY_DENIED -> CameraMessage(
            message = stringResource(R.string.scan_permission_denied),
            actionLabel = stringResource(R.string.scan_permission_settings),
            onAction = onOpenSettings,
            // Dismissing the system dialog looks just like a final refusal, so asking again stays open.
            secondaryActionLabel = stringResource(R.string.scan_permission_allow),
            onSecondaryAction = onRequestPermission,
        )
        access != CameraAccess.GRANTED -> Unit
        unavailable -> CameraMessage(stringResource(R.string.scan_camera_unavailable))
        else -> camera()
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
