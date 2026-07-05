package tools.lunaris.texteditor.feature.drawer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Modifier
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
