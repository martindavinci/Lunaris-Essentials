package tools.lunaris.texteditor.feature.editor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tools.lunaris.texteditor.R
import tools.lunaris.texteditor.document.DocumentSession
import kotlinx.coroutines.launch
import tools.lunaris.texteditor.edit.Indenter
import tools.lunaris.texteditor.edit.SearchEngine
import tools.lunaris.texteditor.edit.SearchOptions
import tools.lunaris.texteditor.edit.TimestampFormatter
import tools.lunaris.texteditor.editor.CodeEditorController
import tools.lunaris.texteditor.feature.editor.action.EditorActionSheet
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarConfig
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarItem
import tools.lunaris.texteditor.feature.editor.toolbar.icon
import tools.lunaris.texteditor.feature.editor.toolbar.labelRes
import tools.lunaris.texteditor.feature.preview.PreviewScreen
import tools.lunaris.texteditor.feature.settings.SettingsViewModel
import tools.lunaris.texteditor.file.FileRef
import tools.lunaris.texteditor.ui.theme.ToolbarCloseAccent
import tools.lunaris.texteditor.ui.theme.ToolbarOpenAccent
import java.time.Instant
import java.time.ZoneId

private val TopBarHeight = 56.dp
private val BarElevation = 3.dp

@Composable
fun EditorScreen(
    onNavigate: (String) -> Unit = {},
    openRequest: tools.lunaris.texteditor.feature.OpenRequest? = null,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val viewSettings by settingsVm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val editorController = remember { CodeEditorController() }

    // Open a file the app was launched/asked to handle (VIEW/EDIT intent). openFile reuses an
    // already-open tab for the same uri, so re-firing on config change is harmless. VIEW intents
    // with no write grant open read-only.
    LaunchedEffect(openRequest) {
        openRequest?.let { req ->
            when {
                req.uri != null -> viewModel.openFile(FileRef(req.uri), readOnly = req.readOnly)
                req.sharedText != null -> viewModel.newTabWithText(req.sharedText)
            }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        remember { OpenDocumentReadWrite() }
    ) { uri ->
        if (uri != null) {
            persistReadWrite(context, uri)
            viewModel.openFile(FileRef(uri.toString()))
        }
    }

    val createLauncher = rememberLauncherForActivityResult(
        // "*/*" (not "text/plain") so the system keeps the name's extension as typed instead of
        // forcing ".txt" — e.g. "name.md" stays "name.md", not "name.md.txt".
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            persistReadWrite(context, uri)
            viewModel.save(FileRef(uri.toString()))
        }
    }

    // Re-pick a file whose grant expired (the picker opens near the original location); selecting
    // it again yields a fresh persisted permission so the file — and its recents entry — reopen.
    val reauthorizeLauncher = rememberLauncherForActivityResult(
        remember { OpenDocumentAtLocation() }
    ) { uri ->
        viewModel.consumeReauthorize()
        if (uri != null) {
            persistReadWrite(context, uri)
            viewModel.openFile(FileRef(uri.toString()))
        }
    }

    var openDialog by remember { mutableStateOf<EditorDialog?>(null) }
    // Preview is an overlay, not a nav destination: navigating away would dispose the editor and
    // lose its cursor position and undo/redo history. Kept on top, the editor stays alive behind it.
    var showPreview by remember { mutableStateOf(false) }
    var showAccessory by remember { mutableStateOf(false) }
    // The unified action menu (⋮) opens this bottom sheet.
    var showSheet by remember { mutableStateOf(false) }
    // Select mode: while on, the cursor arrows extend the selection instead of moving the caret.
    var selectMode by remember { mutableStateOf(false) }
    // Guard app exit (back / ESC) when any tab has unsaved changes.
    var showExitConfirm by remember { mutableStateOf(false) }
    var accessoryInitDone by remember { mutableStateOf(false) }
    LaunchedEffect(viewSettings) {
        if (!accessoryInitDone) { showAccessory = viewSettings.accessoryBarDefault; accessoryInitDone = true }
    }
    var wrapInit by remember { mutableStateOf(false) }
    LaunchedEffect(viewSettings) {
        if (!wrapInit) {
            viewModel.setWordWrap(viewSettings.wordWrap)
            wrapInit = true
        }
    }
    val searchEngine = remember { SearchEngine() }

    // Undo/redo availability, refreshed whenever the document text changes (typing, undo,
    // redo, paste, load all flow through state.text). Drives the toolbar's enabled state.
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    LaunchedEffect(state.text) {
        canUndo = editorController.canUndo()
        canRedo = editorController.canRedo()
    }

    // Closing a tab with unsaved changes asks for confirmation first; clean tabs close directly.
    var closeConfirmId by remember { mutableStateOf<String?>(null) }
    val requestClose: (String) -> Unit = { id ->
        // Remember the caret of the active tab so reopening this file restores it.
        val cursor = if (id == state.activeTabId) editorController.cursorOffset() else null
        if (state.tabs.firstOrNull { it.id == id }?.dirty == true) closeConfirmId = id
        else viewModel.closeTab(id, cursor)
    }

    // Search mode: a floating card in the thumb zone (query + count + prev/next/close) sits above
    // the toolbar while a query is active; the header never changes. A no-match find snackbars.
    val scope = rememberCoroutineScope()
    var searchSession by remember { mutableStateOf<SearchSession?>(null) }
    // All matches for the active query, and the 1-based index of the current one, for the "n/total"
    // counter. Recomputed as the text or query changes.
    val searchMatches = remember(searchSession, state.text) {
        searchSession?.let { searchEngine.findAll(state.text, it.query, it.opts) } ?: emptyList()
    }
    var searchIndex by remember { mutableStateOf(0) }
    val noMatchTemplate = stringResource(R.string.search_no_match)
    val runFind: (Boolean) -> Unit = { forward ->
        searchSession?.let { s ->
            // Search forward from the END of the current selection (else findNext re-finds the
            // current match); search backward from its START.
            val sel = editorController.selection()
            val m = if (forward) searchEngine.findNext(state.text, s.query, sel.last, s.opts)
            else searchEngine.findPrevious(state.text, s.query, sel.first, s.opts)
            if (m != null) {
                editorController.selectRange(m.start, m.end)
                searchIndex = searchMatches.indexOfFirst { it.start == m.start }.let { if (it >= 0) it + 1 else 0 }
            } else scope.launch { snackbar.showSnackbar(String.format(noMatchTemplate, s.query)) }
        }
    }
    val searchCountLabel = if (searchSession == null) "" else "$searchIndex/${searchMatches.size}"
    val startSearch: (String, SearchOptions, Boolean) -> Unit = { q, o, forward ->
        if (q.isNotEmpty()) {
            searchSession = SearchSession(q, o)
            runFind(forward)
        }
    }
    // Search is per-document: dropping it when the active tab changes turns it off on close,
    // switch, new, and open. Matches in one doc make no sense carried into another.
    LaunchedEffect(state.activeTabId) { searchSession = null }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val folderTreeVm: tools.lunaris.texteditor.feature.drawer.FolderTreeViewModel = hiltViewModel()
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) folderTreeVm.pickTree(uri)
    }
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open) folderTreeVm.start()
    }
    val recents by viewModel.recents().collectAsStateWithLifecycle(initialValue = emptyList())

    LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) {
        viewModel.snapshotSession()
    }

    LaunchedEffect(viewSettings.autosave, viewSettings.autosaveIntervalMinutes) {
        if (viewSettings.autosave) {
            val intervalMs = viewSettings.autosaveIntervalMinutes.coerceAtLeast(1) * 60_000L
            while (true) {
                kotlinx.coroutines.delay(intervalMs)
                viewModel.autosave()
            }
        }
    }

    val savedMsg = stringResource(R.string.saved)
    LaunchedEffect(state.savedSignal) {
        if (state.savedSignal > 0) snackbar.showSnackbar(savedMsg)
    }
    // In-place save couldn't write back (e.g. a read-only shared file) → fall back to Save As.
    LaunchedEffect(state.saveAsRequest) {
        if (state.saveAsRequest > 0) createLauncher.launch(suggestedSaveName(state, viewModel.languageCatalog))
    }
    val openFailedMsg = stringResource(R.string.error_open_failed)
    val saveFailedMsg = stringResource(R.string.error_save_failed)
    val tooLargeMsg = stringResource(
        R.string.error_file_too_large,
        (DocumentIo.MAX_OPEN_BYTES / (1024 * 1024)).toInt(),
    )
    val notFoundMsg = stringResource(R.string.error_file_not_found)
    LaunchedEffect(state.errorKind, state.error) {
        val message = when (state.errorKind) {
            EditorErrorKind.OPEN -> openFailedMsg
            EditorErrorKind.SAVE -> saveFailedMsg
            EditorErrorKind.TOO_LARGE -> tooLargeMsg
            EditorErrorKind.NOT_FOUND -> notFoundMsg
            null -> state.error
        }
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.consumeError()
        }
    }

    val applyIndent: (Boolean) -> Unit = { increase ->
        val sel = editorController.selection()
        val t = state.text
        val nt = if (increase) Indenter.increase(t, sel.first, sel.last, viewSettings.indentUnit)
                 else Indenter.decrease(t, sel.first, sel.last, viewSettings.indentUnit)
        if (nt != t) viewModel.onContentChanged(nt)
    }

    // Back / ESC handling, most specific first (a later BackHandler wins when several are enabled):
    // close an open drawer, exit search, then guard app exit when there are unsaved changes.
    val hasUnsaved = state.dirty || state.tabs.any { it.dirty }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = searchSession != null) { searchSession = null }
    BackHandler(enabled = hasUnsaved && drawerState.isClosed && searchSession == null) {
        showExitConfirm = true
    }

    // Hoisted so both the top-bar shortcuts (Search/Save), the bottom toolbar, and the action
    // sheet route through one handler.
    val onEditorMenuAction: (EditorMenuAction) -> Unit = { action ->
        when (action) {
            EditorMenuAction.SYNTAX -> openDialog = EditorDialog.SYNTAX
            EditorMenuAction.DISPLAY_STYLE -> openDialog = EditorDialog.DISPLAY_STYLE
            EditorMenuAction.ENCODING -> openDialog = EditorDialog.ENCODING
            EditorMenuAction.SEARCH -> openDialog = EditorDialog.SEARCH
            EditorMenuAction.GOTO_LINE -> openDialog = EditorDialog.GOTO
            EditorMenuAction.STATS -> openDialog = EditorDialog.STATS
            EditorMenuAction.INSERT_COLOR -> openDialog = EditorDialog.INSERT_COLOR
            EditorMenuAction.INSERT_TIMESTAMP ->
                editorController.insertAtCursor(TimestampFormatter.format(Instant.now(), ZoneId.systemDefault()))
            EditorMenuAction.INDENT_INCREASE -> applyIndent(true)
            EditorMenuAction.INDENT_DECREASE -> applyIndent(false)
            EditorMenuAction.UNDO -> editorController.undo()
            EditorMenuAction.REDO -> editorController.redo()
            EditorMenuAction.SELECT_ALL -> editorController.selectAll()
            EditorMenuAction.CUT -> editorController.cut()
            EditorMenuAction.COPY -> editorController.copy()
            EditorMenuAction.PASTE -> editorController.paste()
            EditorMenuAction.WORD_WRAP -> viewModel.toggleWordWrap()
            EditorMenuAction.READ_ONLY -> viewModel.toggleReadOnly()
            EditorMenuAction.TOOLBAR -> openDialog = EditorDialog.TOOLBAR
            EditorMenuAction.ACCESSORY_BAR -> showAccessory = !showAccessory
            EditorMenuAction.SHARE ->
                shareDocumentAsFile(context, suggestedSaveName(state, viewModel.languageCatalog), state.text)
            EditorMenuAction.PREVIEW -> { viewModel.previewActive(); showPreview = true }
            EditorMenuAction.NEW_TAB -> viewModel.newTab()
            EditorMenuAction.OPEN -> openLauncher.launch(arrayOf("*/*"))
            EditorMenuAction.OPEN_TREE -> treeLauncher.launch(null)
            EditorMenuAction.SAVE -> {
                val ref = state.fileRef
                if (ref != null) viewModel.save(ref) else createLauncher.launch(suggestedSaveName(state, viewModel.languageCatalog))
            }
            EditorMenuAction.SAVE_AS -> createLauncher.launch(suggestedSaveName(state, viewModel.languageCatalog))
            EditorMenuAction.RELOAD -> if (state.fileRef != null) viewModel.reopenWithEncoding(state.charset)
            EditorMenuAction.CLOSE -> requestClose(state.activeTabId)
            EditorMenuAction.INPUT_METHOD ->
                context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    ?.showInputMethodPicker()
        }
    }

    EditorScaffold(
        state = state,
        viewModel = viewModel,
        folderTreeVm = folderTreeVm,
        recents = recents,
        drawerState = drawerState,
        snackbar = snackbar,
        editorController = editorController,
        showAccessory = showAccessory,
        selectMode = selectMode,
        onNavigate = onNavigate,
        folderDrawerEnabled = viewSettings.folderDrawerEnabled,
        lineNumbers = viewSettings.lineNumbers,
        fontSizeSp = viewSettings.fontSizeSp.toFloat(),
        lineSpacingExtra = viewSettings.lineSpacingExtra,
        monospace = viewSettings.monospace,
        canUndo = canUndo,
        canRedo = canRedo,
        onRequestClose = requestClose,
        searchActive = searchSession != null,
        searchQuery = searchSession?.query ?: "",
        searchCountLabel = searchCountLabel,
        onSearchBack = { searchSession = null },
        onSearchPrev = { runFind(false) },
        onSearchNext = { runFind(true) },
        onMenuAction = onEditorMenuAction,
        onOpenSheet = { showSheet = true },
        onToolbarItem = { item ->
            when (item) {
                ToolbarItem.OPEN -> openLauncher.launch(arrayOf("*/*"))
                ToolbarItem.PASTE -> editorController.paste()
                ToolbarItem.UNDO -> editorController.undo()
                ToolbarItem.REDO -> editorController.redo()
                ToolbarItem.SEARCH -> openDialog = EditorDialog.SEARCH
                ToolbarItem.CURSOR_LEFT ->
                    if (selectMode) editorController.extendSelection(-1) else editorController.moveCursor(-1)
                ToolbarItem.CURSOR_RIGHT ->
                    if (selectMode) editorController.extendSelection(1) else editorController.moveCursor(1)
                ToolbarItem.SELECT_MODE -> {
                    selectMode = !selectMode
                    if (selectMode) editorController.beginSelect()
                }
                ToolbarItem.ACCESSORY -> showAccessory = !showAccessory
                ToolbarItem.SAVE -> {
                    val ref = state.fileRef
                    if (ref != null) viewModel.save(ref) else createLauncher.launch(suggestedSaveName(state, viewModel.languageCatalog))
                }
                ToolbarItem.CLOSE -> requestClose(state.activeTabId)
                ToolbarItem.PREVIEW -> { viewModel.previewActive(); showPreview = true }
            }
        },
        onPickFolder = { treeLauncher.launch(null) },
    )

    EditorDialogs(
        open = openDialog,
        state = state,
        viewModel = viewModel,
        controller = editorController,
        searchEngine = searchEngine,
        onStartSearch = startSearch,
        onDismiss = { openDialog = null },
    )

    if (showSheet) {
        EditorActionSheet(
            checked = buildSet {
                if (state.wordWrap) add(EditorMenuAction.WORD_WRAP)
                if (state.readOnly) add(EditorMenuAction.READ_ONLY)
                if (showAccessory) add(EditorMenuAction.ACCESSORY_BAR)
            },
            disabled = buildSet {
                if (state.text.isBlank()) add(EditorMenuAction.SHARE)
                if (state.fileRef == null) add(EditorMenuAction.RELOAD)
                if (!state.dirty) add(EditorMenuAction.SAVE)
            },
            onAction = onEditorMenuAction,
            onDismiss = { showSheet = false },
        )
    }

    closeConfirmId?.let { id ->
        AlertDialog(
            onDismissRequest = { closeConfirmId = null },
            confirmButton = {
                TextButton(onClick = {
                    val cursor = if (id == state.activeTabId) editorController.cursorOffset() else null
                    viewModel.closeTab(id, cursor); closeConfirmId = null
                }) {
                    Text(stringResource(R.string.close_confirm_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { closeConfirmId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.close_confirm_title)) },
            text = { Text(stringResource(R.string.close_confirm_message)) },
        )
    }

    state.reauthorizeUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { viewModel.consumeReauthorize() },
            confirmButton = {
                TextButton(onClick = { reauthorizeLauncher.launch(Uri.parse(uri)) }) {
                    Text(stringResource(R.string.reopen_reselect))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.consumeReauthorize() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.reopen_title)) },
            text = { Text(stringResource(R.string.reopen_message)) },
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            confirmButton = {
                TextButton(onClick = { showExitConfirm = false; (context as? Activity)?.finish() }) {
                    Text(stringResource(R.string.exit_confirm_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.exit_confirm_title)) },
            text = { Text(stringResource(R.string.exit_confirm_message)) },
        )
    }

    if (showPreview) {
        BackHandler { showPreview = false }
        // Opaque, full-screen so the editor behind it is fully covered but never disposed.
        Surface(modifier = Modifier.fillMaxSize()) {
            PreviewScreen(onBack = { showPreview = false })
        }
    }
}

@Composable
internal fun EditorTopBar(
    title: String,
    subtitle: String? = null,
    saveEnabled: Boolean = true,
    onMenu: () -> Unit = {},
    showMenu: Boolean = true,
    onSearch: () -> Unit = {},
    onSave: () -> Unit = {},
    onOpenSheet: () -> Unit = {},
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = BarElevation) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(TopBarHeight)
                .padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showMenu) {
                IconButton(onClick = onMenu) {
                    Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.nav_menu))
                }
            }
            val shownTitle = if (title == DocumentSession.UNTITLED) stringResource(R.string.untitled) else title
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shownTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.menu_search))
            }
            IconButton(onClick = onSave, enabled = saveEnabled) {
                Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.menu_save))
            }
            IconButton(onClick = onOpenSheet) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu_more))
            }
        }
    }
}

/** Active search query + options driving the editor's search mode. */
data class SearchSession(val query: String, val opts: SearchOptions)

/**
 * Floating search card for the thumb zone (sits above the toolbar while a query is active) — the
 * header stays untouched. Shows the query, the "n/total" match count, and prev/next/close. Tapping
 * the query text reopens the search dialog to edit it.
 */
@Composable
internal fun SearchField(
    query: String,
    countLabel: String,
    onEditQuery: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.height(52.dp).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = query.ifBlank { stringResource(R.string.menu_search) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (query.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onEditQuery)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            )
            if (countLabel.isNotBlank()) {
                Text(
                    text = countLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            IconButton(onClick = onPrev) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.search_find_prev))
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.search_find_next))
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_back))
            }
        }
    }
}

private val GroupGap = 10.dp
private val BarButtonSize = 40.dp

@Composable
internal fun EditorBottomBar(
    config: ToolbarConfig,
    onItem: (ToolbarItem) -> Unit,
    isEnabled: (ToolbarItem) -> Boolean = { true },
    toggled: (ToolbarItem) -> Boolean = { false },
) {
    val items = config.enabled
    // Preview is pinned to the far right; the rest cluster on the left, spilling any that don't
    // fit into a trailing "…" overflow menu.
    val leftItems = items.filter { it.barGroup() != BarGroup.PREVIEW }
    val previewItems = items.filter { it.barGroup() == BarGroup.PREVIEW }
    val cells = remember(leftItems) { groupCells(leftItems) }
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = BarElevation) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Float above the IME when the keyboard is up; fall back to the nav bar otherwise
                // (union picks the larger inset, so the toolbar is never hidden behind the keyboard).
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AdaptiveBarCells(cells, onItem, isEnabled, toggled, Modifier.weight(1f))
            if (previewItems.isNotEmpty()) {
                Spacer(Modifier.width(GroupGap))
                previewItems.forEach { PreviewButton(it, onItem, isEnabled(it)) }
            }
        }
    }
}

/** Group consecutive same-[barGroup] items into cells; each cell becomes one pill or icon run. */
private fun groupCells(items: List<ToolbarItem>): List<List<ToolbarItem>> {
    val out = mutableListOf<List<ToolbarItem>>()
    var i = 0
    while (i < items.size) {
        val g = items[i].barGroup()
        var j = i + 1
        while (j < items.size && items[j].barGroup() == g) j++
        out += items.subList(i, j).toList()
        i = j
    }
    return out
}

/**
 * Lays out the left [cells] left-to-right, measuring each; any that don't fit collapse into a
 * trailing "…" menu pinned to the right edge (so the visible bar never clips or scrolls).
 */
@Composable
private fun AdaptiveBarCells(
    cells: List<List<ToolbarItem>>,
    onItem: (ToolbarItem) -> Unit,
    isEnabled: (ToolbarItem) -> Boolean,
    toggled: (ToolbarItem) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    SubcomposeLayout(modifier) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gapPx = GroupGap.roundToPx()
        val overflowWidth = BarButtonSize.roundToPx()
        val placeables = cells.mapIndexed { idx, cell ->
            subcompose(idx) { BarCell(cell, onItem, isEnabled, toggled) }.first().measure(loose)
        }
        val n = placeables.size
        fun prefixWidth(k: Int): Int {
            var w = 0
            for (idx in 0 until k) { if (idx > 0) w += gapPx; w += placeables[idx].width }
            return w
        }
        // Unbounded width (e.g. previews) fits everything; otherwise find the largest prefix that
        // fits, reserving room for the overflow button when some cells spill.
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else prefixWidth(n)
        var fit = n
        if (prefixWidth(n) > maxW) {
            fit = 0
            for (k in n - 1 downTo 0) {
                val lead = if (k > 0) gapPx else 0
                if (prefixWidth(k) + lead + overflowWidth <= maxW) { fit = k; break }
            }
        }
        val overflow = cells.drop(fit).flatten()
        val overflowPlaceable = if (overflow.isNotEmpty()) {
            subcompose("overflow") {
                OverflowButton(overflow, expanded, { expanded = it }, onItem, isEnabled)
            }.first().measure(loose)
        } else null
        // One thin divider sits in each gap between adjacent placed cells (single homogeneous track).
        val dividers = (1 until fit).map { i -> subcompose("div$i") { BarDivider() }.first().measure(loose) }

        val placed = placeables.take(fit)
        val height = (placed.map { it.height } + listOfNotNull(overflowPlaceable?.height)).maxOrNull() ?: 0
        layout(maxW, height) {
            var x = 0
            placed.forEachIndexed { idx, p ->
                if (idx > 0) {
                    val d = dividers[idx - 1]
                    d.placeRelative(x + (gapPx - d.width) / 2, (height - d.height) / 2)
                    x += gapPx
                }
                p.placeRelative(x, (height - p.height) / 2)
                x += p.width
            }
            overflowPlaceable?.placeRelative(maxW - overflowPlaceable.width, (height - overflowPlaceable.height) / 2)
        }
    }
}

/** One group of actions, rendered as a flat icon run (no pill) — dividers separate cells. */
@Composable
private fun BarCell(
    cell: List<ToolbarItem>,
    onItem: (ToolbarItem) -> Unit,
    isEnabled: (ToolbarItem) -> Boolean,
    toggled: (ToolbarItem) -> Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        cell.forEach { BarIconButton(it, onItem, isEnabled(it), toggled(it)) }
    }
}

/** Thin vertical divider between toolbar groups. */
@Composable
private fun BarDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(24.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
    )
}

/** "…" button whose menu lists the overflowed actions as icon + label rows (close in red). */
@Composable
private fun OverflowButton(
    overflow: List<ToolbarItem>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onItem: (ToolbarItem) -> Unit,
    isEnabled: (ToolbarItem) -> Boolean,
) {
    Box {
        IconButton(onClick = { onExpandedChange(true) }, modifier = Modifier.size(BarButtonSize)) {
            Icon(Icons.Filled.MoreHoriz, contentDescription = stringResource(R.string.toolbar_more), modifier = Modifier.size(24.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            overflow.forEach { item ->
                val red = item == ToolbarItem.CLOSE
                DropdownMenuItem(
                    enabled = isEnabled(item),
                    text = { Text(stringResource(item.labelRes()), color = if (red) ToolbarCloseAccent else Color.Unspecified) },
                    leadingIcon = {
                        Icon(item.icon(), contentDescription = null, tint = if (red) ToolbarCloseAccent else LocalContentColor.current)
                    },
                    onClick = { onExpandedChange(false); onItem(item) },
                )
            }
        }
    }
}

/** Logical grouping that drives the segmented bottom bar. */
private enum class BarGroup { NAV, HISTORY, KEYS, FILE, PREVIEW, SOLO }

private fun ToolbarItem.barGroup(): BarGroup = when (this) {
    ToolbarItem.CURSOR_LEFT, ToolbarItem.CURSOR_RIGHT, ToolbarItem.SELECT_MODE -> BarGroup.NAV
    ToolbarItem.UNDO, ToolbarItem.REDO -> BarGroup.HISTORY
    ToolbarItem.ACCESSORY -> BarGroup.KEYS
    ToolbarItem.OPEN, ToolbarItem.CLOSE -> BarGroup.FILE
    ToolbarItem.PREVIEW -> BarGroup.PREVIEW
    else -> BarGroup.SOLO
}

/**
 * Uniform flat toolbar icon — the single treatment for every left-track action. Open/close keep
 * their yellow/red accents; a live toggle (select mode, special chars) tints to primary; the rest
 * use the default content colour.
 */
@Composable
private fun BarIconButton(item: ToolbarItem, onItem: (ToolbarItem) -> Unit, enabled: Boolean, active: Boolean) {
    val tint = when {
        item == ToolbarItem.OPEN -> ToolbarOpenAccent
        item == ToolbarItem.CLOSE -> ToolbarCloseAccent
        active -> MaterialTheme.colorScheme.primary
        else -> LocalContentColor.current
    }
    IconButton(onClick = { onItem(item) }, enabled = enabled, modifier = Modifier.size(BarButtonSize)) {
        Icon(item.icon(), contentDescription = stringResource(item.labelRes()), tint = tint, modifier = Modifier.size(24.dp))
    }
}

/** Preview: the one filled accent circle, pinned to the right of the bar. */
@Composable
private fun PreviewButton(item: ToolbarItem, onItem: (ToolbarItem) -> Unit, enabled: Boolean) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
        IconButton(onClick = { onItem(item) }, enabled = enabled, modifier = Modifier.size(BarButtonSize)) {
            Icon(
                item.icon(),
                contentDescription = stringResource(item.labelRes()),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** OpenDocument variant that also requests WRITE so opened files can be saved back. */
private class OpenDocumentReadWrite : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
}

/**
 * Open-document picker that takes the (expired) uri as input and hints the system picker to start
 * near it via EXTRA_INITIAL_URI, so the user re-selects the same file with minimal navigation.
 */
private class OpenDocumentAtLocation : ActivityResultContract<Uri, Uri?>() {
    override fun createIntent(context: Context, input: Uri): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, input)
            }
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

/** Persists read+write access, degrading to read-only, never crashing. */
private fun persistReadWrite(context: Context, uri: Uri) {
    val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    try {
        context.contentResolver.takePersistableUriPermission(uri, readWrite)
    } catch (_: SecurityException) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // No persistable permission offered; URI remains valid for this task only.
        }
    }
}
