package tools.lunaris.texteditor.feature.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tools.lunaris.texteditor.R
import tools.lunaris.texteditor.data.RecentEntry
import tools.lunaris.texteditor.editor.CodeEditorController
import tools.lunaris.texteditor.editor.CodeEditorView
import tools.lunaris.texteditor.feature.drawer.DrawerSection
import tools.lunaris.texteditor.feature.drawer.DrawerShell
import tools.lunaris.texteditor.feature.drawer.ExtensionsPanel
import tools.lunaris.texteditor.feature.drawer.FolderTreeViewModel
import tools.lunaris.texteditor.feature.drawer.FoldersPanel
import tools.lunaris.texteditor.feature.drawer.RecentsPanel
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarItem
import tools.lunaris.texteditor.file.FileRef

@Composable
internal fun EditorScaffold(
    state: EditorUiState,
    viewModel: EditorViewModel,
    folderTreeVm: FolderTreeViewModel,
    recents: List<RecentEntry>,
    drawerState: DrawerState,
    snackbar: SnackbarHostState,
    editorController: CodeEditorController,
    showAccessory: Boolean,
    selectMode: Boolean,
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
    searchCountLabel: String,
    onSearchBack: () -> Unit,
    onSearchPrev: () -> Unit,
    onSearchNext: () -> Unit,
    onOpenSheet: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    // Subtitle: the current language name, plus a "not saved" suffix while the doc is dirty.
    // Plain text (no grammar) falls back to a localized label instead of the catalog's English.
    val languageName = viewModel.languageCatalog.displayName(state.syntax, state.scopeName)
        ?: stringResource(R.string.syntax_plain)
    val subtitle = if (state.dirty) {
        stringResource(R.string.subtitle_dirty, languageName, stringResource(R.string.unsaved))
    } else languageName

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Open only via the hamburger icon (edge-swipe-to-open fought document scrolling),
        // but allow swipe-to-close once the drawer is already open.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                var section by rememberSaveable { mutableStateOf(DrawerSection.RECENTS) }
                val treeState by folderTreeVm.state.collectAsStateWithLifecycle()
                DrawerShell(
                    section = section,
                    onSection = { section = it },
                    onSettings = {
                        scope.launch { drawerState.close() }
                        onNavigate(tools.lunaris.texteditor.feature.Routes.SETTINGS)
                    },
                    recentsSlot = {
                        RecentsPanel(
                            recents = recents,
                            currentUri = state.fileRef?.uri,
                            nowMillis = System.currentTimeMillis(),
                            onOpen = { uri -> viewModel.openFile(FileRef(uri)); scope.launch { drawerState.close() } },
                            onNewTab = { viewModel.newTab(); scope.launch { drawerState.close() } },
                        )
                    },
                    foldersSlot = {
                        FoldersPanel(
                            state = treeState,
                            onToggle = folderTreeVm::toggle,
                            onOpenFile = { ref -> viewModel.openFile(FileRef(ref)); scope.launch { drawerState.close() } },
                            onPickFolder = onPickFolder,
                            onNewTab = { viewModel.newTab(); scope.launch { drawerState.close() } },
                            onCreateFile = folderTreeVm::createFile,
                            onCreateFolder = folderTreeVm::createFolder,
                            onRename = folderTreeVm::rename,
                            onCloseRoot = folderTreeVm::closeRoot,
                        )
                    },
                    extensionsSlot = { ExtensionsPanel() },
                )
            }
        },
    ) {
        Scaffold(
            // Slightly translucent snackbar so it sits more gently over the document.
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    // Tap anywhere on it to dismiss immediately.
                    Snackbar(
                        snackbarData = data,
                        modifier = Modifier.clickable { data.dismiss() },
                        containerColor = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.82f),
                    )
                }
            },
            topBar = {
                // The header never changes — search lives in a floating card down in the thumb zone.
                EditorTopBar(
                    title = state.title,
                    subtitle = subtitle,
                    saveEnabled = state.dirty,
                    onMenu = { scope.launch { drawerState.open() } },
                    showMenu = folderDrawerEnabled,
                    onSearch = { onMenuAction(EditorMenuAction.SEARCH) },
                    onSave = { onMenuAction(EditorMenuAction.SAVE) },
                    onOpenSheet = onOpenSheet,
                )
            },
            bottomBar = {
                Column {
                    if (searchActive) {
                        SearchField(
                            query = searchQuery,
                            countLabel = searchCountLabel,
                            onEditQuery = { onMenuAction(EditorMenuAction.SEARCH) },
                            onPrev = onSearchPrev,
                            onNext = onSearchNext,
                            onClose = onSearchBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 6.dp),
                        )
                    }
                    EditorBottomBar(
                        config = state.toolbar,
                        onItem = onToolbarItem,
                        isEnabled = { item ->
                            when (item) {
                                ToolbarItem.UNDO -> canUndo
                                ToolbarItem.REDO -> canRedo
                                ToolbarItem.SAVE -> state.dirty
                                else -> true
                            }
                        },
                        toggled = { item ->
                            (item == ToolbarItem.ACCESSORY && showAccessory) ||
                                (item == ToolbarItem.SELECT_MODE && selectMode)
                        },
                    )
                }
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
                    gotoOffset = state.pendingGoToOffset,
                    onGotoOffsetConsumed = viewModel::consumeGoToOffset,
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
