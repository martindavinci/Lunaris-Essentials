package tools.lunaris.texteditor.feature.editor

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tools.lunaris.texteditor.edit.ColorLiteral
import tools.lunaris.texteditor.edit.SearchEngine
import tools.lunaris.texteditor.edit.SearchOptions
import tools.lunaris.texteditor.edit.TextStats
import tools.lunaris.texteditor.editor.CodeEditorController
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarConfigDialog

/** Which modal dialog (if any) is currently shown. */
enum class EditorDialog { SYNTAX, DISPLAY_STYLE, ENCODING, SEARCH, GOTO, STATS, INSERT_COLOR, TOOLBAR }

/** Single host that renders whichever editor dialog is open. */
@Composable
fun EditorDialogs(
    open: EditorDialog?,
    state: EditorUiState,
    viewModel: EditorViewModel,
    controller: CodeEditorController,
    searchEngine: SearchEngine,
    onStartSearch: (String, SearchOptions, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    when (open) {
        null -> Unit
        EditorDialog.SYNTAX ->
            SyntaxPickerDialog(viewModel.languageCatalog, state.syntax, { viewModel.setSyntax(it) }, onDismiss)
        EditorDialog.DISPLAY_STYLE ->
            ColorSchemePickerDialog(viewModel.colorSchemeCatalog, state.colorSchemeId, { viewModel.setColorScheme(it) }, onDismiss)
        EditorDialog.ENCODING ->
            EncodingPickerDialog(viewModel.charsetCatalog, state.charset, { viewModel.reopenWithEncoding(it) }, onDismiss)
        EditorDialog.SEARCH -> SearchDialog(
            history = viewModel.searchHistory.collectAsStateWithLifecycle().value,
            onFind = { q, o -> onStartSearch(q, o, true); onDismiss() },
            onFindPrev = { q, o -> onStartSearch(q, o, false); onDismiss() },
            onReplace = { q, r, o ->
                val sel = controller.selection()
                val m = searchEngine.findNext(state.text, q, sel.first, o)
                if (m != null && m.start == sel.first && m.end == sel.last) {
                    viewModel.onContentChanged(state.text.replaceRange(m.start, m.end, r))
                } else m?.let { controller.selectRange(it.start, it.end) }
            },
            onReplaceAll = { q, r, o ->
                val res = searchEngine.replaceAll(state.text, q, r, o)
                if (res.count > 0) viewModel.onContentChanged(res.text)
            },
            onDismiss = onDismiss,
        )
        EditorDialog.GOTO ->
            GoToLineDialog(state.text.count { it == '\n' } + 1, { controller.goToLine(it) }, onDismiss)
        EditorDialog.STATS -> {
            val s = TextStats.of(state.text)
            StatsDialog(s.words, s.characters, onDismiss)
        }
        EditorDialog.INSERT_COLOR ->
            InsertColorDialog({ controller.insertAtCursor(ColorLiteral.hex(it)) }, onDismiss)
        EditorDialog.TOOLBAR ->
            ToolbarConfigDialog(
                initial = state.toolbar,
                onApply = { viewModel.setToolbar(it) },
                onDismiss = onDismiss,
            )
    }
}
