package io.github.martindavinci.lunaris.scanner.feature.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.ui.theme.LunarisScannerTheme

private val IconSize = 60.dp
private val OrSize = 44.dp
private val AreaPadding = 24.dp
private val AreaSpacing = 12.dp

/** The whole screen is the choice: half for codes, half for documents, side by side on a wide screen. */
@Composable
internal fun ScanChoiceScreen(onCode: () -> Unit, onDocument: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val code = @Composable { modifier: Modifier ->
            ChoiceArea(
                icon = Icons.Filled.QrCodeScanner,
                name = R.string.scan_mode_code,
                detail = R.string.scan_mode_code_detail,
                background = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onCode,
                modifier = modifier,
            )
        }
        val document = @Composable { modifier: Modifier ->
            ChoiceArea(
                icon = Icons.Filled.Description,
                name = R.string.scan_mode_document,
                detail = R.string.scan_mode_document_detail,
                background = MaterialTheme.colorScheme.surface,
                content = MaterialTheme.colorScheme.onSurface,
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = onDocument,
                modifier = modifier,
            )
        }
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                code(Modifier.weight(1f).fillMaxHeight())
                document(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                code(Modifier.weight(1f).fillMaxWidth())
                document(Modifier.weight(1f).fillMaxWidth())
            }
        }
        Box(
            Modifier.size(OrSize).background(MaterialTheme.colorScheme.background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.scan_mode_or),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChoiceArea(
    icon: ImageVector,
    name: Int,
    detail: Int,
    background: Color,
    content: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(AreaPadding),
        verticalArrangement = Arrangement.spacedBy(AreaSpacing, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(IconSize))
        Text(stringResource(name), style = MaterialTheme.typography.headlineSmall, color = content, textAlign = TextAlign.Center)
        Text(stringResource(detail), style = MaterialTheme.typography.bodyMedium, color = content, textAlign = TextAlign.Center)
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ScanChoiceScreenPreview() {
    LunarisScannerTheme {
        ScanChoiceScreen(onCode = {}, onDocument = {})
    }
}
