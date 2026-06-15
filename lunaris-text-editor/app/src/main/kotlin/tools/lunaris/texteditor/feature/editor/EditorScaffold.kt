package tools.lunaris.texteditor.feature.editor

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    onNavigate: (String) -> Unit,
    folderDrawerEnabled: Boolean,
    onMenuAction: (EditorMenuAction) -> Unit,
    onToolbarItem: (ToolbarItem) -> Unit,
    onPickFolder: () -> Unit,
    lineNumbers: Boolean,
    fontSizeSp: Float,
    lineSpacingExtra: Int,
    monospace: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onRequestClose: (String) -> Unit,
    searchActive: Boolean,
    searchQuery: String,
    onSearchBack: () -> Unit,
    onSearchPrev: () -> Unit,
    onSearchNext: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Open only via the hamburger icon (edge-swipe-to-open fought document scrolling),
        // but allow swipe-to-close once the drawer is already open.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                NavigationDrawerItem(
                    label = { Text(stringResource(EditorMenuAction.RECENT_FILES.labelRes)) },
                    icon = { Icon(Icons.Filled.History, contentDescription = null) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onMenuAction(EditorMenuAction.RECENT_FILES) },
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(EditorMenuAction.BOOKMARKS.labelRes)) },
                    icon = { Icon(Icons.Filled.Bookmark, contentDescription = null) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onMenuAction(EditorMenuAction.BOOKMARKS) },
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(EditorMenuAction.FILE_ASSOCIATIONS.labelRes)) },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onMenuAction(EditorMenuAction.FILE_ASSOCIATIONS) },
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(EditorMenuAction.STORAGE_MANAGER.labelRes)) },
                    icon = { Icon(Icons.Filled.Storage, contentDescription = null) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onMenuAction(EditorMenuAction.STORAGE_MANAGER) },
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(EditorMenuAction.SETTINGS.labelRes)) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onMenuAction(EditorMenuAction.SETTINGS) },
                )
                HorizontalDivider()
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
                if (searchActive) {
                    SearchTopBar(
                        query = searchQuery,
                        onBack = onSearchBack,
                        onPrev = onSearchPrev,
                        onNext = onSearchNext,
                    )
                } else EditorTopBar(
                    title = state.title,
                    checked = buildSet {
                        if (state.wordWrap) add(EditorMenuAction.WORD_WRAP)
                        if (state.readOnly) add(EditorMenuAction.READ_ONLY)
                        if (showAccessory) add(EditorMenuAction.ACCESSORY_BAR)
                    },
                    disabled = buildSet {
                        if (state.text.isBlank()) add(EditorMenuAction.SHARE)
                        if (state.fileRef == null) add(EditorMenuAction.RELOAD)
                        if (!canUndo) add(EditorMenuAction.UNDO)
                        if (!canRedo) add(EditorMenuAction.REDO)
                    },
                    onMenu = { scope.launch { drawerState.open() } },
                    onMenuAction = onMenuAction,
                    showMenu = folderDrawerEnabled,
                )
            },
            bottomBar = {
                EditorBottomBar(
                    config = state.toolbar,
                    onItem = onToolbarItem,
                    isEnabled = { item ->
                        when (item) {
                            ToolbarItem.UNDO -> canUndo
                            ToolbarItem.REDO -> canRedo
                            else -> true
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                // The tab strip only appears with 2+ open files; a single file gets the full
                // height. "New" stays reachable from the nav drawer.
                if (state.tabs.size > 1) {
                    TabStrip(
                        tabs = state.tabs,
                        activeTabId = state.activeTabId,
                        onSelect = viewModel::switchTab,
                        onClose = onRequestClose,
                        onNew = viewModel::newTab,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                CodeEditorView(
                    text = state.text,
                    onTextChange = viewModel::onContentChanged,
                    scopeName = state.scopeName,
                    themeId = state.colorSchemeId,
                    controller = editorController,
                    wordWrap = state.wordWrap,
                    readOnly = state.readOnly,
                    lineNumbers = lineNumbers,
                    fontSizeSp = fontSizeSp,
                    lineSpacingExtra = lineSpacingExtra,
                    monospace = monospace,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    gotoLine = state.pendingGoToLine,
                    onGotoConsumed = viewModel::consumeGoToLine,
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
