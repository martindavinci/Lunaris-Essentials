package tools.lunaris.texteditor.feature.editor

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import tools.lunaris.texteditor.editor.CodeEditorController
import tools.lunaris.texteditor.editor.CodeEditorView
import tools.lunaris.texteditor.feature.browser.FolderBrowserViewModel
import tools.lunaris.texteditor.feature.browser.FolderDrawer
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarItem
import tools.lunaris.texteditor.file.FileRef

@Composable
internal fun EditorScaffold(
    state: EditorUiState,
    viewModel: EditorViewModel,
    folderVm: FolderBrowserViewModel,
    drawerState: DrawerState,
    snackbar: SnackbarHostState,
    editorController: CodeEditorController,
    showAccessory: Boolean,
    context: Context,
    onMenuAction: (EditorMenuAction) -> Unit,
    onToolbarItem: (ToolbarItem) -> Unit,
    onPickFolder: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                FolderDrawer(
                    onOpenFile = { ref ->
                        viewModel.openFile(FileRef(ref))
                        scope.launch { drawerState.close() }
                    },
                    onPickFolder = onPickFolder,
                    onEnableAllFiles = { context.startActivity(folderVm.allFilesIntent()) },
                    viewModel = folderVm,
                )
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                EditorTopBar(
                    title = state.title,
                    checked = buildSet {
                        if (state.wordWrap) add(EditorMenuAction.WORD_WRAP)
                        if (state.readOnly) add(EditorMenuAction.READ_ONLY)
                        if (showAccessory) add(EditorMenuAction.ACCESSORY_BAR)
                    },
                    onMenu = { scope.launch { drawerState.open() } },
                    onMenuAction = onMenuAction,
                )
            },
            bottomBar = {
                EditorBottomBar(config = state.toolbar, onItem = onToolbarItem)
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                TabStrip(
                    tabs = state.tabs,
                    activeTabId = state.activeTabId,
                    onSelect = viewModel::switchTab,
                    onClose = viewModel::closeTab,
                    onNew = viewModel::newTab,
                    modifier = Modifier.fillMaxWidth(),
                )
                CodeEditorView(
                    text = state.text,
                    onTextChange = viewModel::onContentChanged,
                    scopeName = state.scopeName,
                    themeId = state.colorSchemeId,
                    controller = editorController,
                    wordWrap = state.wordWrap,
                    readOnly = state.readOnly,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                if (showAccessory) {
                    AccessoryBar(
                        onKey = { editorController.insertAtCursor(it) },
                        onCursorLeft = { editorController.moveCursor(-1) },
                        onCursorRight = { editorController.moveCursor(1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
