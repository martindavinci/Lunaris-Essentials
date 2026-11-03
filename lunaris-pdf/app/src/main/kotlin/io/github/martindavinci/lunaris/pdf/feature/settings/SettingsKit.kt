package io.github.martindavinci.lunaris.pdf.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.ui.theme.iconTile

private val TopBarMinHeight = 64.dp
private val TitlePaddingAfterBack = 8.dp
private val SectionHorizontalPadding = 16.dp
private val SectionTopPadding = 16.dp
private val SectionTitleInset = 8.dp
private val SectionTitleBottomPadding = 8.dp
private val CardShape = RoundedCornerShape(20.dp)
private val RowHorizontalPadding = 16.dp
private val RowVerticalPadding = 12.dp
private val RowSpacing = 16.dp
private val TileSize = 40.dp
private val TileShape = RoundedCornerShape(12.dp)
private val TileIconSize = 22.dp
private val DialogListMaxHeight = 400.dp

/** System bar insets are already handled by the nav host. */
@Composable
fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = TopBarMinHeight), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = TitlePaddingAfterBack),
        )
    }
}

/** A titled card holding the rows of one subject. */
@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = SectionHorizontalPadding, end = SectionHorizontalPadding, top = SectionTopPadding)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = SectionTitleInset, bottom = SectionTitleBottomPadding),
        )
        Surface(shape = CardShape, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
        Box(
            modifier = Modifier.size(TileSize).background(MaterialTheme.colorScheme.iconTile, TileShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(TileIconSize))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun SettingsToggleRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SettingsRow(icon, title, subtitle, onClick = { onCheckedChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SingleChoiceDialog(
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
