package tools.lunaris.texteditor.feature.drawer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tools.lunaris.texteditor.R

private data class PendingPrompt(val title: String, val confirm: String, val onConfirm: (String) -> Unit, val initial: String = "")

@Composable
fun FoldersPanel(
    state: FolderTreeUiState,
    onToggle: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onPickFolder: () -> Unit,
    onNewTab: () -> Unit,
    onCreateFile: (String, String) -> Unit,
    onCreateFolder: (String, String) -> Unit,
    onRename: (String, String) -> Unit,
    onCloseRoot: (String) -> Unit,
) {
    var prompt by remember { mutableStateOf<PendingPrompt?>(null) }
    var confirmCloseRef by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.drawer_folders), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onPickFolder) { Icon(Icons.Filled.CreateNewFolder, contentDescription = stringResource(R.string.folder_add)) }
        }
        if (!state.accessGranted) {
            OutlinedButton(onClick = onPickFolder, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Icon(Icons.Filled.Folder, contentDescription = null)
                Text(stringResource(R.string.folder_pick), Modifier.padding(start = 8.dp))
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(state.nodes, key = { it.ref }) { node ->
                FolderRow(
                    node = node,
                    onToggle = { onToggle(node.ref) },
                    onOpenFile = { onOpenFile(node.ref) },
                    menu = {
                        FolderNodeMenu(
                            node = node,
                            onCreateFile = { onCreateFile(node.ref, it) },
                            onCreateFolder = { onCreateFolder(node.ref, it) },
                            onRename = { onRename(node.ref, it) },
                            onRequestClose = { confirmCloseRef = node.ref },
                            setPrompt = { prompt = it },
                        )
                    },
                )
            }
        }
        Button(onClick = onNewTab, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.drawer_new_file), Modifier.padding(start = 8.dp))
        }
    }
    prompt?.let { p ->
        NameInputDialog(title = p.title, initial = p.initial, confirmLabel = p.confirm, onConfirm = { p.onConfirm(it); prompt = null }, onDismiss = { prompt = null })
    }
    confirmCloseRef?.let { ref ->
        AlertDialog(
            onDismissRequest = { confirmCloseRef = null },
            title = { Text(stringResource(R.string.folder_close_title)) },
            confirmButton = { TextButton(onClick = { onCloseRoot(ref); confirmCloseRef = null }) { Text(stringResource(R.string.folder_close)) } },
            dismissButton = { TextButton(onClick = { confirmCloseRef = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun FolderRow(node: TreeNode, onToggle: () -> Unit, onOpenFile: () -> Unit, menu: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable { if (node.isDirectory) onToggle() else onOpenFile() }
            .padding(start = (12 + node.depth * 16).dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (node.isDirectory) {
            Icon(
                if (node.expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.folder_expand),
            )
            Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
        } else {
            Icon(fileTypeIcon(node.name), contentDescription = null, modifier = Modifier.padding(start = 24.dp))
        }
        Text(node.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
        if (node.isDirectory) {
            node.childCount?.let {
                Text(
                    "$it",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            menu()
        }
    }
}

@Composable
private fun FolderNodeMenu(
    node: TreeNode,
    onCreateFile: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRename: (String) -> Unit,
    onRequestClose: () -> Unit,
    setPrompt: (PendingPrompt) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val newFileT = stringResource(R.string.folder_new_file)
    val newSubT = stringResource(R.string.folder_new_subfolder)
    val renameT = stringResource(R.string.folder_rename)
    val ok = stringResource(R.string.ok)
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.folder_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(newFileT) }, onClick = { open = false; setPrompt(PendingPrompt(newFileT, ok, onCreateFile)) })
            DropdownMenuItem(text = { Text(newSubT) }, onClick = { open = false; setPrompt(PendingPrompt(newSubT, ok, onCreateFolder)) })
            DropdownMenuItem(text = { Text(renameT) }, onClick = { open = false; setPrompt(PendingPrompt(renameT, ok, onRename, initial = node.name)) })
            if (node.depth == 0) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.folder_close), color = MaterialTheme.colorScheme.error) },
                    onClick = { open = false; onRequestClose() },
                )
            }
        }
    }
}
