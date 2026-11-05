package io.github.martindavinci.lunaris.scanner.feature.scan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.feature.result.ResultAction
import io.github.martindavinci.lunaris.scanner.feature.result.labelRes
import io.github.martindavinci.lunaris.scanner.model.ScannedCode

private val LineSpacing = 8.dp
private val SmallIcon = 18.dp
private val OutlineWidth = 1.dp

@Composable
internal fun CardWarnings(card: CodeCard) {
    if (card.blocked) CardWarning(stringResource(R.string.result_warning_blocked))
    card.warnings.forEach { CardWarning(stringResource(it.labelRes)) }
}

@Composable
private fun CardWarning(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(LineSpacing), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(SmallIcon))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun CardButtons(card: CodeCard, onDetails: (ScannedCode) -> Unit, onRun: (ResultAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(LineSpacing), verticalAlignment = Alignment.CenterVertically) {
        when (val main = card.main) {
            is CardAction.OpenLink -> Button(onClick = { onRun(ResultAction.OpenUrl(main.url)) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.scan_open))
            }
            CardAction.OpenDetails -> Button(onClick = { onDetails(card.code) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.scan_open_details))
            }
            CardAction.None -> Unit
        }
        OutlinedButton(
            onClick = { onRun(ResultAction.Copy(card.code.text, card.sensitive)) },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.copy))
        }
        OutlinedIconButton(
            onClick = { onRun(ResultAction.Share(card.code.text)) },
            border = BorderStroke(OutlineWidth, MaterialTheme.colorScheme.outline),
        ) {
            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share))
        }
    }
}
