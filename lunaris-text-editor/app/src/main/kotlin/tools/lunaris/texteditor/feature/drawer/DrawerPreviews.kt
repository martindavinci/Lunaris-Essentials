package tools.lunaris.texteditor.feature.drawer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Modifier
import tools.lunaris.texteditor.data.RecentEntry
import tools.lunaris.texteditor.ui.theme.LunarisEditorTheme

@Preview(showBackground = true)
@Composable
private fun NavRailPreview() {
    LunarisEditorTheme { NavRail(DrawerSection.RECENTS, {}, {}, {}) }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun DrawerShellPreview() {
    LunarisEditorTheme {
        DrawerShell(
            section = DrawerSection.RECENTS,
            onSection = {},
            onEditor = {},
            onSettings = {},
            recentsSlot = { Text("Recents", modifier = Modifier.fillMaxSize()) },
            foldersSlot = { Text("Folders", modifier = Modifier.fillMaxSize()) },
            extensionsSlot = { Text("Extensions", modifier = Modifier.fillMaxSize()) },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NameInputDialogPreview() {
    LunarisEditorTheme {
        NameInputDialog(title = "New folder", confirmLabel = "Create", onConfirm = {}, onDismiss = {})
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun RecentsPanelPreview() {
    val nowMillis = 1_700_000_000_000L
    val recents = listOf(
        RecentEntry("content://recent/1", "notes.txt", nowMillis - 30_000L),
        RecentEntry("content://recent/2", "main.kt", nowMillis - 5 * 60_000L),
        RecentEntry("content://recent/3", "index.html", nowMillis - 3 * 3_600_000L),
        RecentEntry("content://recent/4", "config.json", nowMillis - 30 * 3_600_000L),
        RecentEntry("content://recent/5", "README.md", nowMillis - 5 * 86_400_000L),
        RecentEntry("content://recent/6", "archive.log", nowMillis - 40L * 86_400_000L),
    )
    LunarisEditorTheme {
        RecentsPanel(recents = recents, currentUri = "content://recent/2", nowMillis = nowMillis, onOpen = {}, onNewTab = {})
    }
}
