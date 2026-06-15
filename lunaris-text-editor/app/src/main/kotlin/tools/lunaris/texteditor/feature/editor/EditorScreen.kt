package tools.lunaris.texteditor.feature.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import tools.lunaris.texteditor.feature.browser.FolderBrowserViewModel
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarConfig
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarItem
import tools.lunaris.texteditor.feature.editor.toolbar.icon
import tools.lunaris.texteditor.feature.editor.toolbar.labelRes
import tools.lunaris.texteditor.feature.settings.SettingsViewModel
import tools.lunaris.texteditor.file.FileRef
import java.time.Instant
import java.time.ZoneId

private val TopBarHeight = 56.dp
private val BarElevation = 3.dp

@Composable
fun EditorScreen(
    onNavigate: (String) -> Unit = {},
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val viewSettings by settingsVm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val editorController = remember { CodeEditorController() }

    val openLauncher = rememberLauncherForActivityResult(
        remember { OpenDocumentReadWrite() }
    ) { uri ->
        if (uri != null) {
            persistReadWrite(context, uri)
            viewModel.openFile(FileRef(uri.toString()))
        }
    }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            persistReadWrite(context, uri)
            viewModel.save(FileRef(uri.toString()))
        }
    }

    var openDialog by remember { mutableStateOf<EditorDialog?>(null) }
    var showAccessory by remember { mutableStateOf(false) }
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
        if (state.tabs.firstOrNull { it.id == id }?.dirty == true) closeConfirmId = id
        else viewModel.closeTab(id)
    }

    // Search mode: a persistent top bar (back + prev/next) replaces the normal one while a
    // query is active. A no-match find shows a quick snackbar.
    val scope = rememberCoroutineScope()
    var searchSession by remember { mutableStateOf<SearchSession?>(null) }
    val noMatchTemplate = stringResource(R.string.search_no_match)
    val runFind: (Boolean) -> Unit = { forward ->
        searchSession?.let { s ->
            // Search forward from the END of the current selection (else findNext re-finds the
            // current match); search backward from its START.
            val sel = editorController.selection()
            val m = if (forward) searchEngine.findNext(state.text, s.query, sel.last, s.opts)
            else searchEngine.findPrevious(state.text, s.query, sel.first, s.opts)
            if (m != null) editorController.selectRange(m.start, m.end)
            else scope.launch { snackbar.showSnackbar(String.format(noMatchTemplate, s.query)) }
        }
    }
    val startSearch: (String, SearchOptions, Boolean) -> Unit = { q, o, forward ->
        if (q.isNotEmpty()) {
            searchSession = SearchSession(q, o)
            runFind(forward)
        }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val folderVm: FolderBrowserViewModel = hiltViewModel()
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) folderVm.onTreePicked(uri)
    }
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open) folderVm.start()
    }

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
    val openFailedMsg = stringResource(R.string.error_open_failed)
    val saveFailedMsg = stringResource(R.string.error_save_failed)
    LaunchedEffect(state.errorKind, state.error) {
        val message = when (state.errorKind) {
            EditorErrorKind.OPEN -> openFailedMsg
            EditorErrorKind.SAVE -> saveFailedMsg
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

    EditorScaffold(
        state = state,
        viewModel = viewModel,
        folderVm = folderVm,
        drawerState = drawerState,
        snackbar = snackbar,
        editorController = editorController,
        showAccessory = showAccessory,
        context = context,
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
        onSearchBack = { searchSession = null },
        onSearchPrev = { runFind(false) },
        onSearchNext = { runFind(true) },
        onMenuAction = { action ->
            when (action) {
                EditorMenuAction.SYNTAX -> openDialog = EditorDialog.SYNTAX
                EditorMenuAction.DISPLAY_STYLE -> openDialog = EditorDialog.DISPLAY_STYLE
                EditorMenuAction.ENCODING -> openDialog = EditorDialog.ENCODING
                EditorMenuAction.RECENT_FILES -> openDialog = EditorDialog.RECENT
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
                EditorMenuAction.PASTE -> editorController.paste()
                EditorMenuAction.WORD_WRAP -> viewModel.toggleWordWrap()
                EditorMenuAction.READ_ONLY -> viewModel.toggleReadOnly()
                EditorMenuAction.TOOLBAR -> openDialog = EditorDialog.TOOLBAR
                EditorMenuAction.ACCESSORY_BAR -> showAccessory = !showAccessory
                EditorMenuAction.SHARE -> {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, state.text)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }
                EditorMenuAction.FILE_ASSOCIATIONS ->
                    onNavigate(tools.lunaris.texteditor.feature.Routes.ASSOCIATIONS)
                EditorMenuAction.BOOKMARKS -> openDialog = EditorDialog.BOOKMARKS
                EditorMenuAction.ADD_BOOKMARK -> viewModel.addBookmark(editorController.currentLine())
                EditorMenuAction.STORAGE_MANAGER -> onNavigate(tools.lunaris.texteditor.feature.Routes.STORAGE)
                EditorMenuAction.SETTINGS -> onNavigate(tools.lunaris.texteditor.feature.Routes.SETTINGS)
                EditorMenuAction.RUN -> { viewModel.previewActive(); onNavigate(tools.lunaris.texteditor.feature.Routes.PREVIEW) }
                EditorMenuAction.NEW_TAB -> viewModel.newTab()
            }
        },
        onToolbarItem = { item ->
            when (item) {
                ToolbarItem.OPEN -> openLauncher.launch(arrayOf("*/*"))
                ToolbarItem.PASTE -> editorController.paste()
                ToolbarItem.UNDO -> editorController.undo()
                ToolbarItem.REDO -> editorController.redo()
                ToolbarItem.SEARCH -> openDialog = EditorDialog.SEARCH
                ToolbarItem.CURSOR_LEFT -> editorController.moveCursor(-1)
                ToolbarItem.CURSOR_RIGHT -> editorController.moveCursor(1)
                ToolbarItem.SAVE -> {
                    val ref = state.fileRef
                    if (ref != null) viewModel.save(ref) else createLauncher.launch(state.title)
                }
                ToolbarItem.CLOSE -> requestClose(state.activeTabId)
                ToolbarItem.RUN -> { viewModel.previewActive(); onNavigate(tools.lunaris.texteditor.feature.Routes.PREVIEW) }
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

    closeConfirmId?.let { id ->
        AlertDialog(
            onDismissRequest = { closeConfirmId = null },
            confirmButton = {
                TextButton(onClick = { viewModel.closeTab(id); closeConfirmId = null }) {
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
}

@Composable
internal fun EditorTopBar(
    title: String,
    checked: Set<EditorMenuAction> = emptySet(),
    disabled: Set<EditorMenuAction> = emptySet(),
    onMenu: () -> Unit = {},
    showMenu: Boolean = true,
    onMenuAction: (EditorMenuAction) -> Unit,
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
            Text(
                text = shownTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            var menuOpen by remember { mutableStateOf(false) }
            // Wrap the button + menu in a Box so the dropdown anchors to the ⋮ button
            // (top-right) instead of drifting to the left edge.
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu_more))
                }
                EditorOverflowMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    onAction = onMenuAction,
                    checked = checked,
                    disabled = disabled,
                )
            }
        }
    }
}

/** Active search query + options driving the editor's search mode. */
data class SearchSession(val query: String, val opts: SearchOptions)

/** Top bar shown while search mode is active: exit + query + previous/next match. */
@Composable
internal fun SearchTopBar(
    query: String,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
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
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(
                text = stringResource(R.string.search_active_title, query),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onPrev) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.search_find_prev))
            }
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.search_find_next))
            }
        }
    }
}

@Composable
internal fun EditorBottomBar(
    config: ToolbarConfig,
    onItem: (ToolbarItem) -> Unit,
    isEnabled: (ToolbarItem) -> Boolean = { true },
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = BarElevation) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            config.enabled.forEach { item ->
                // Fixed 40dp slots so a full toolbar (9-10 items) fits the width without the
                // last icon being squished. Icons render at a uniform 24dp.
                IconButton(
                    onClick = { onItem(item) },
                    enabled = isEnabled(item),
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        item.icon(),
                        contentDescription = stringResource(item.labelRes()),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
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
