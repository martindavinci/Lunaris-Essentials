package tools.lunaris.texteditor.feature.browser

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import tools.lunaris.texteditor.files.FileEntry
import tools.lunaris.texteditor.ui.theme.LunarisEditorTheme

private val sampleFolderState = FolderUiState(
    accessGranted = true,
    location = "/storage/emulated/0/Documents",
    entries = listOf(
        FileEntry(name = "src", ref = "/Documents/src", isDirectory = true, extension = ""),
        FileEntry(name = "main.kt", ref = "/Documents/main.kt", isDirectory = false, extension = "kt"),
        FileEntry(name = "README.md", ref = "/Documents/README.md", isDirectory = false, extension = "md"),
    ),
    canGoUp = true,
    filterEnabled = true,
)

@Preview(showBackground = true)
@Composable
private fun FolderDrawerContentPreview() {
    LunarisEditorTheme {
        FolderDrawerContent(
            state = sampleFolderState,
            onOpenFile = {},
            onPickFolder = {},
            onNavigateTo = {},
            onUp = {},
            onCloseFolder = {},
            onToggleFilter = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FolderDrawerNoAccessPreview() {
    LunarisEditorTheme {
        FolderDrawerContent(
            state = FolderUiState(accessGranted = false),
            onOpenFile = {},
            onPickFolder = {},
            onNavigateTo = {},
            onUp = {},
            onCloseFolder = {},
            onToggleFilter = {},
        )
    }
}
