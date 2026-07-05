package tools.lunaris.texteditor.feature.drawer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun DrawerShell(
    section: DrawerSection,
    onSection: (DrawerSection) -> Unit,
    onSettings: () -> Unit,
    recentsSlot: @Composable () -> Unit,
    foldersSlot: @Composable () -> Unit,
    extensionsSlot: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        NavRail(section, onSection, onSettings)
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (section) {
                DrawerSection.RECENTS -> recentsSlot()
                DrawerSection.FOLDERS -> foldersSlot()
                DrawerSection.EXTENSIONS -> extensionsSlot()
            }
        }
    }
}
