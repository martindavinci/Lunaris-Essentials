package io.github.martindavinci.lunaris.scanner.feature.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.parse.withVisibleBidiControls
import io.github.martindavinci.lunaris.scanner.model.UrlWarning
import java.text.DateFormat
import java.util.Date

private val CardPadding = 16.dp
private val IconTextGap = 8.dp

/** Warnings about a link. Shown above everything else, and above the button that opens it. */
@Composable
internal fun UrlNoticeCard(warnings: List<UrlWarning>, blocked: Boolean) {
    if (warnings.isEmpty() && !blocked) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(ResultLineSpacing)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Text(
                    text = stringResource(R.string.result_warning_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = IconTextGap),
                )
            }
            if (blocked) Text(stringResource(R.string.result_warning_blocked))
            warnings.forEach { Text(stringResource(it.labelRes)) }
        }
    }
}

@Composable
internal fun ResultFieldsCard(fields: List<ResultField>, secretRevealed: Boolean, onToggleSecret: () -> Unit) {
    if (fields.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(ResultSectionSpacing)) {
            fields.forEach { field ->
                val label = stringResource(field.label.labelRes)
                when (field) {
                    is ResultField.Plain -> FieldRow(label, field.value)
                    is ResultField.Secret -> SecretRow(label, field.value, secretRevealed, onToggleSecret)
                    is ResultField.Time -> FieldRow(label, formatTime(field.millis))
                    is ResultField.Security -> FieldRow(label, stringResource(field.security.labelRes))
                    is ResultField.Flag -> FieldRow(label, stringResource(R.string.result_yes))
                }
            }
        }
    }
}

@Composable
private fun formatTime(millis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(millis, locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(millis))
    }
}

@Composable
private fun FieldRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(value.withVisibleBidiControls(), style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
private fun SecretRow(label: String, value: String, revealed: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FieldRow(
            label = label,
            value = if (revealed) value else stringResource(R.string.result_password_mask),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = stringResource(
                    if (revealed) R.string.result_password_hide else R.string.result_password_show,
                ),
            )
        }
    }
}

/** The action of the code's own type stands out; copying and sharing stay secondary. */
@Composable
internal fun ResultActionButtons(actions: List<ResultAction>, onRun: (ResultAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ResultLineSpacing)) {
        actions.forEach { action ->
            val modifier = Modifier.fillMaxWidth()
            when (action) {
                is ResultAction.Copy, is ResultAction.Share, is ResultAction.CopyPassword ->
                    OutlinedButton(onClick = { onRun(action) }, modifier = modifier) { ActionLabel(action) }
                else -> Button(onClick = { onRun(action) }, modifier = modifier) { ActionLabel(action) }
            }
        }
    }
}

@Composable
private fun ActionLabel(action: ResultAction) {
    Icon(action.icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text(stringResource(action.labelRes))
}

@Composable
internal fun RawTextCard(text: String, hidden: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ResultLineSpacing)) {
        Text(
            text = stringResource(R.string.result_raw_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hidden) {
            Text(stringResource(R.string.result_raw_hidden), style = MaterialTheme.typography.bodyMedium)
        } else {
            SelectionContainer {
                Text(
                    text = text.withVisibleBidiControls(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
