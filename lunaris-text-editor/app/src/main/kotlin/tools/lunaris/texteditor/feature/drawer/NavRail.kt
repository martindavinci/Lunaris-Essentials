package tools.lunaris.texteditor.feature.drawer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tools.lunaris.texteditor.R

private val RailWidth = 72.dp

@Composable
fun NavRail(selected: DrawerSection, onSelect: (DrawerSection) -> Unit, onEditor: () -> Unit, onSettings: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column(
            Modifier.fillMaxHeight().width(RailWidth).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RailButton(Icons.Filled.EditNote, stringResource(R.string.drawer_editor), selected = false, accent = true, onClick = onEditor)
            Spacer(Modifier.height(8.dp))
            RailButton(Icons.Filled.History, stringResource(R.string.drawer_recents), selected == DrawerSection.RECENTS) { onSelect(DrawerSection.RECENTS) }
            RailButton(Icons.Filled.Folder, stringResource(R.string.drawer_folders), selected == DrawerSection.FOLDERS) { onSelect(DrawerSection.FOLDERS) }
            RailButton(Icons.Filled.Code, stringResource(R.string.drawer_extensions), selected == DrawerSection.EXTENSIONS) { onSelect(DrawerSection.EXTENSIONS) }
            Spacer(Modifier.weight(1f))
            RailButton(Icons.Filled.Settings, stringResource(R.string.menu_settings), selected = false, onClick = onSettings)
        }
    }
}

@Composable
private fun RailButton(icon: ImageVector, label: String, selected: Boolean, accent: Boolean = false, onClick: () -> Unit) {
    val bg = when {
        accent -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.secondaryContainer
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val tint = when {
        accent -> MaterialTheme.colorScheme.onPrimary
        selected -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> LocalContentColor.current
    }
    Surface(shape = RoundedCornerShape(percent = 30), color = bg, modifier = Modifier.padding(vertical = 4.dp)) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = label, tint = tint) }
    }
}
