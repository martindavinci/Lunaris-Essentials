package io.github.martindavinci.lunaris.pdf.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R

private val BarPadding = 8.dp
private val FieldHeight = 48.dp
private val FieldShape = RoundedCornerShape(24.dp)
private val FieldPadding = 16.dp
private val ChipRowStart = 56.dp
private val ChipSpacing = 8.dp
private val ChipIconSize = 18.dp
private val NavHeight = 72.dp
private val NavPadding = 16.dp
private val NavButtonSize = 44.dp

/** The query field stays on top, where the keyboard leaves it in sight. The search runs as it is typed. */
@Composable
internal fun ReaderSearchBar(search: SearchState, onAction: (ReaderAction) -> Unit) {
    var query by rememberSaveable { mutableStateOf(search.query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val change: (String) -> Unit = {
        query = it
        onAction(ReaderAction.SubmitSearch(it))
    }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(vertical = BarPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onAction(ReaderAction.CloseSearch) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_close_search))
                }
                Surface(
                    shape = FieldShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f).padding(end = BarPadding),
                ) {
                    Row(
                        modifier = Modifier.heightIn(min = FieldHeight).padding(start = FieldPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = query,
                            onValueChange = change,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onAction(ReaderAction.FindNext) }),
                            modifier = Modifier.weight(1f).focusRequester(focus),
                        )
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { change("") }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.reader_clear_search))
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.padding(start = ChipRowStart),
                horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
            ) {
                OptionChip(R.string.reader_match_case, search.caseSensitive) { onAction(ReaderAction.ToggleMatchCase) }
                OptionChip(R.string.reader_whole_words, search.wholeWords) { onAction(ReaderAction.ToggleWholeWords) }
            }
        }
    }
}

@Composable
private fun OptionChip(label: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(label)) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(ChipIconSize)) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.primaryContainer,
            selectedLabelColor = colors.onPrimaryContainer,
            selectedLeadingIconColor = colors.onPrimaryContainer,
        ),
    )
}

/** Previous and next under the thumb, above the keyboard when it is open. */
@Composable
internal fun ReaderSearchNav(search: SearchState, onAction: (ReaderAction) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.imePadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(min = NavHeight)
                .padding(horizontal = NavPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavButton(Icons.Filled.KeyboardArrowUp, R.string.reader_find_previous, search.total > 0) {
                onAction(ReaderAction.FindPrevious)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(countLabel(search), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                if (search.total > 0 && search.page > 0) {
                    Text(
                        text = stringResource(R.string.reader_search_where, search.page, search.onPage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            NavButton(Icons.Filled.KeyboardArrowDown, R.string.reader_find_next, search.total > 0) {
                onAction(ReaderAction.FindNext)
            }
        }
    }
}

@Composable
private fun countLabel(search: SearchState): String = when {
    search.total > 0 -> stringResource(R.string.reader_search_count, search.current, search.total)
    search.searching -> stringResource(R.string.reader_searching)
    search.query.trim().length >= 2 -> stringResource(R.string.reader_search_none)
    else -> stringResource(R.string.reader_search_hint)
}

@Composable
private fun NavButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(NavButtonSize),
    ) {
        Icon(icon, contentDescription = stringResource(label), modifier = Modifier.padding(BarPadding))
    }
}
