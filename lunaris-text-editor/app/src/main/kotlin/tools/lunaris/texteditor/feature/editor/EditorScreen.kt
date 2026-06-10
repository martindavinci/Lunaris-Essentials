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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import tools.lunaris.texteditor.edit.Indenter
import tools.lunaris.texteditor.edit.SearchEngine
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
private const val AUTOSAVE_INTERVAL_MS = 60_000L
private const val INDENT_UNIT = "    "

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
    val searchEngine = remember { SearchEngine() }

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

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(AUTOSAVE_INTERVAL_MS)
            viewModel.autosave()
        }
    }

    val savedMsg = stringResource(R.string.saved)
    LaunchedEffect(state.savedSignal) {
        if (state.savedSignal > 0) snackbar.showSnackbar(savedMsg)
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    val applyIndent: (Boolean) -> Unit = { increase ->
        val sel = editorController.selection()
        val t = state.text
        val nt = if (increase) Indenter.increase(t, sel.first, sel.last, INDENT_UNIT)
                 else Indenter.decrease(t, sel.first, sel.last, INDENT_UNIT)
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
        lineNumbers = viewSettings.lineNumbers,
        fontSizeSp = viewSettings.fontSizeSp.toFloat(),
        lineSpacingExtra = viewSettings.lineSpacingExtra,
        monospace = viewSettings.monospace,
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
                ToolbarItem.CLOSE -> viewModel.closeTab(state.activeTabId)
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
        onDismiss = { openDialog = null },
    )
}

@Composable
internal fun EditorTopBar(
    title: String,
    checked: Set<EditorMenuAction> = emptySet(),
    onMenu: () -> Unit = {},
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
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.nav_menu))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            var menuOpen by remember { mutableStateOf(false) }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu_more))
            }
            EditorOverflowMenu(
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                onAction = onMenuAction,
                checked = checked,
            )
        }
    }
}

@Composable
internal fun EditorBottomBar(config: ToolbarConfig, onItem: (ToolbarItem) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = BarElevation) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            config.enabled.forEach { item ->
                IconButton(onClick = { onItem(item) }) {
                    Icon(item.icon(), contentDescription = stringResource(item.labelRes()))
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
