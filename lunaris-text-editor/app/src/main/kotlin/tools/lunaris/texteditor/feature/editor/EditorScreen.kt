package tools.lunaris.texteditor.feature.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import tools.lunaris.texteditor.editor.CodeEditorView
import tools.lunaris.texteditor.file.FileRef
import java.time.Instant
import java.time.ZoneId

private val TopBarHeight = 56.dp
private val BarElevation = 3.dp
private const val AUTOSAVE_INTERVAL_MS = 60_000L
private const val INDENT_UNIT = "    "

@Composable
fun EditorScreen(viewModel: EditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
    val searchEngine = remember { SearchEngine() }

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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            EditorTopBar(title = state.title) { action ->
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
                }
            }
        },
        bottomBar = {
            EditorBottomBar(
                onOpen = { openLauncher.launch(arrayOf("*/*")) },
                onNew = { viewModel.newTab() },
                onSave = {
                    val ref = state.fileRef
                    if (ref != null) viewModel.save(ref) else createLauncher.launch(state.title)
                },
                onClose = { viewModel.closeTab(state.activeTabId) },
            )
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
                wordWrap = true,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }

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
private fun EditorTopBar(
    title: String,
    onMenuAction: (EditorMenuAction) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = BarElevation) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(TopBarHeight)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            )
        }
    }
}

@Composable
private fun EditorBottomBar(
    onOpen: () -> Unit,
    onNew: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = BarElevation) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = onOpen) {
                Icon(Icons.Filled.FolderOpen, contentDescription = stringResource(R.string.action_open))
            }
            IconButton(onClick = onNew) {
                Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = stringResource(R.string.action_new))
            }
            IconButton(onClick = onSave) {
                Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.action_save))
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
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
