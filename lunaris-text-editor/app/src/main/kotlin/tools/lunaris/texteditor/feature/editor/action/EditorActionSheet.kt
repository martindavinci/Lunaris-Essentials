package tools.lunaris.texteditor.feature.editor.action

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tools.lunaris.texteditor.R
import tools.lunaris.texteditor.feature.editor.EditorMenuAction

/**
 * The unified editor action menu. Replaces the old File/Edit/overflow dropdowns with one
 * searchable bottom sheet whose actions are grouped by [ACTION_SECTIONS]. Stateless apart from
 * the local search query — action routing is delegated to [onAction] (unchanged from before).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorActionSheet(
    checked: Set<EditorMenuAction>,
    disabled: Set<EditorMenuAction>,
    onAction: (EditorMenuAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current

    // Filter by localized label; empty query shows everything.
    fun matches(a: EditorMenuAction): Boolean =
        query.isBlank() || context.getString(a.labelRes).contains(query, ignoreCase = true)

    val visibleSections = ACTION_SECTIONS
        .map { it.copy(items = it.items.filter(::matches)) }
        .filter { it.items.isNotEmpty() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.sheet_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (visibleSections.isEmpty()) {
                Text(
                    stringResource(R.string.sheet_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    textAlign = TextAlign.Center,
                )
            }
            visibleSections.forEach { section ->
                Text(
                    stringResource(section.group.titleRes()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    section.items.forEach { action ->
                        ActionTile(
                            action = action,
                            enabled = action !in disabled,
                            active = action in checked,
                            tint = tintFor(action),
                            onClick = { onDismiss(); onAction(action) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun tintFor(action: EditorMenuAction): Color = when (action) {
    EditorMenuAction.CLOSE -> MaterialTheme.colorScheme.error       // destructive
    EditorMenuAction.PREVIEW -> MaterialTheme.colorScheme.primary   // accent
    else -> MaterialTheme.colorScheme.onSurface
}
