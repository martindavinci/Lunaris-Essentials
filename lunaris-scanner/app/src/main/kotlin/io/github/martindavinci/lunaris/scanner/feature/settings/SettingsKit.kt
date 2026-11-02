package io.github.martindavinci.lunaris.scanner.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.scanner.R

private val TopBarElevation = 3.dp
private val TopBarMinHeight = 56.dp
private val TitlePaddingAfterBack = 8.dp
private val RowPadding = 16.dp
private val SectionTopPadding = 20.dp
private val SectionBottomPadding = 4.dp
private val DialogListMaxHeight = 400.dp

/** The back arrow appears only with [onBack]. System bar insets are already handled by the nav host. */
@Composable
internal fun SettingsTopBar(title: String, onBack: (() -> Unit)? = null) {
    Surface(tonalElevation = TopBarElevation) {
        Row(Modifier.fillMaxWidth().heightIn(min = TopBarMinHeight), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = if (onBack == null) RowPadding else TitlePaddingAfterBack),
            )
        }
    }
}

@Composable
internal fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = RowPadding, end = RowPadding, top = SectionTopPadding, bottom = SectionBottomPadding),
    )
}

@Composable
internal fun SettingsToggleRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(RowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun SettingsChoiceRow(title: String, currentValue: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(RowPadding)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(currentValue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SettingsNavRow(title: String, onClick: () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(RowPadding),
    )
}

@Composable
internal fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var choice by remember { mutableIntStateOf(selectedIndex) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSelect(choice); onDismiss() }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = DialogListMaxHeight).verticalScroll(rememberScrollState())) {
                options.forEachIndexed { i, label ->
                    Row(Modifier.fillMaxWidth().selectable(choice == i) { choice = i }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = choice == i, onClick = { choice = i })
                        Text(label)
                    }
                }
            }
        },
    )
}
