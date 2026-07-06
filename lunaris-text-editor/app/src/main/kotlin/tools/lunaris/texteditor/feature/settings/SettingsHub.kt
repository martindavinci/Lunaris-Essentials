package tools.lunaris.texteditor.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tools.lunaris.texteditor.BuildConfig
import tools.lunaris.texteditor.R

@Composable
internal fun SettingsHub(onOpen: (SettingsCategory) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        SettingsCategoryCard(Icons.Filled.EditNote, stringResource(R.string.cat_editor_title), stringResource(R.string.cat_editor_summary)) { onOpen(SettingsCategory.EDITOR) }
        SettingsCategoryCard(Icons.Filled.Palette, stringResource(R.string.cat_display_title), stringResource(R.string.cat_display_summary)) { onOpen(SettingsCategory.DISPLAY) }
        SettingsCategoryCard(Icons.Filled.Keyboard, stringResource(R.string.cat_keyboard_title), stringResource(R.string.cat_keyboard_summary)) { onOpen(SettingsCategory.KEYBOARD) }
        SettingsCategoryCard(Icons.Filled.FolderOpen, stringResource(R.string.cat_files_title), stringResource(R.string.cat_files_summary)) { onOpen(SettingsCategory.FILES) }
        Spacer(Modifier.height(12.dp))
        SettingsCategoryCard(Icons.Filled.Info, stringResource(R.string.cat_info_title), stringResource(R.string.cat_info_summary)) { onOpen(SettingsCategory.INFO) }
        Spacer(Modifier.height(16.dp))
        Text(
            text = BuildConfig.VERSION_NAME,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SettingsCategoryCard(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(8.dp).size(24.dp),
                )
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
