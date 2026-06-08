package tools.lunaris.texteditor.feature.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tools.lunaris.texteditor.R
import tools.lunaris.texteditor.editor.CodeEditorView
import tools.lunaris.texteditor.file.FileRef

private val TopBarHeight = 56.dp
private val BarElevation = 3.dp

@Composable
fun EditorScreen(viewModel: EditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val openLauncher = rememberLauncherForActivityResult(
        remember { OpenDocumentReadWrite() }
    ) { uri ->
        if (uri != null) {
            persistReadWrite(context, uri)
            viewModel.load(FileRef(uri.toString()))
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

    var showSyntax by remember { mutableStateOf(false) }
    var showScheme by remember { mutableStateOf(false) }
    var showEncoding by remember { mutableStateOf(false) }

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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            EditorTopBar(
                title = state.title,
                onSyntax = { showSyntax = true },
                onScheme = { showScheme = true },
                onEncoding = { showEncoding = true },
            )
        },
        bottomBar = {
            EditorBottomBar(
                onOpen = { openLauncher.launch(arrayOf("*/*")) },
                onNew = { viewModel.newFile() },
                onSave = {
                    val ref = state.fileRef
                    if (ref != null) viewModel.save(ref) else createLauncher.launch(state.title)
                },
            )
        },
    ) { padding ->
        CodeEditorView(
            text = state.text,
            onTextChange = viewModel::onContentChanged,
            scopeName = state.scopeName,
            themeId = state.colorSchemeId,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }

    if (showSyntax) {
        SyntaxPickerDialog(
            catalog = viewModel.languageCatalog,
            current = state.syntax,
            onSelected = viewModel::setSyntax,
            onDismiss = { showSyntax = false },
        )
    }
    if (showScheme) {
        ColorSchemePickerDialog(
            catalog = viewModel.colorSchemeCatalog,
            currentId = state.colorSchemeId,
            onSelected = viewModel::setColorScheme,
            onDismiss = { showScheme = false },
        )
    }
    if (showEncoding) {
        EncodingPickerDialog(
            catalog = viewModel.charsetCatalog,
            current = state.charset,
            onSelected = viewModel::reopenWithEncoding,
            onDismiss = { showEncoding = false },
        )
    }
}

@Composable
private fun EditorTopBar(
    title: String,
    onSyntax: () -> Unit,
    onScheme: () -> Unit,
    onEncoding: () -> Unit,
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
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_syntax)) },
                    onClick = { menuOpen = false; onSyntax() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_display_style)) },
                    onClick = { menuOpen = false; onScheme() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_encoding)) },
                    onClick = { menuOpen = false; onEncoding() },
                )
            }
        }
    }
}

@Composable
private fun EditorBottomBar(
    onOpen: () -> Unit,
    onNew: () -> Unit,
    onSave: () -> Unit,
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
