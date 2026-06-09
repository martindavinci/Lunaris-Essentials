package tools.lunaris.texteditor.feature.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tools.lunaris.texteditor.R

@Composable
fun FolderDrawer(
    onOpenFile: (String) -> Unit,
    onPickFolder: () -> Unit,
    onEnableAllFiles: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FolderBrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier.fillMaxWidth().padding(8.dp)) {
        if (!state.accessGranted) {
            Text(stringResource(R.string.folder_no_access), Modifier.padding(8.dp))
            Button(onClick = onPickFolder, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                Text(stringResource(R.string.folder_pick))
            }
            Button(onClick = onEnableAllFiles, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                Text(stringResource(R.string.folder_enable_all_files))
            }
            return@Column
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.up() }, enabled = state.canGoUp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.folder_up))
            }
            Text(state.location.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.folder_hide_unknown), Modifier.weight(1f))
            Switch(checked = state.filterEnabled, onCheckedChange = { viewModel.toggleFilter(it) })
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(state.entries, key = { it.ref }) { entry ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { if (entry.isDirectory) viewModel.navigateTo(entry.ref) else onOpenFile(entry.ref) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                        contentDescription = stringResource(if (entry.isDirectory) R.string.folder_is_directory else R.string.folder_is_file),
                    )
                    Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}
