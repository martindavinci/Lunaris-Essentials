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

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun FoldersPanelPreview() {
    val nodes = listOf(
        TreeNode(ref = "content://tree/root", name = "Project", isDirectory = true, depth = 0, expanded = true, childCount = 3),
        TreeNode(ref = "content://tree/root/README.md", name = "README.md", isDirectory = false, depth = 1, expanded = false, childCount = null),
        TreeNode(ref = "content://tree/root/src", name = "src", isDirectory = true, depth = 1, expanded = true, childCount = 2),
        TreeNode(ref = "content://tree/root/src/main.kt", name = "main.kt", isDirectory = false, depth = 2, expanded = false, childCount = null),
        TreeNode(ref = "content://tree/root/src/utils.kt", name = "utils.kt", isDirectory = false, depth = 2, expanded = false, childCount = null),
        TreeNode(ref = "content://tree/root/assets", name = "assets", isDirectory = true, depth = 1, expanded = false, childCount = 5),
    )
    LunarisEditorTheme {
        FoldersPanel(
            state = FolderTreeUiState(accessGranted = true, nodes = nodes),
            onToggle = {},
            onOpenFile = {},
            onPickFolder = {},
            onNewTab = {},
            onCreateFile = { _, _ -> },
            onCreateFolder = { _, _ -> },
            onRename = { _, _ -> },
            onCloseRoot = {},
        )
    }
}
