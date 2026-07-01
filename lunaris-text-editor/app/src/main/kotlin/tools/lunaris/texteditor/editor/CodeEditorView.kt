package tools.lunaris.texteditor.editor

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * Above this many characters, TextMate highlighting is dropped (plain text). Grammar tokenizing a
 * very large buffer stalls the UI; the editor stays responsive without colors. ~500k chars covers
 * normal source files with plenty of headroom.
 */
private const val LARGE_FILE_CHARS = 500_000

@Composable
fun CodeEditorView(
    text: String,
    onTextChange: (String) -> Unit,
    scopeName: String?,
    themeId: String,
    controller: CodeEditorController,
    wordWrap: Boolean,
    readOnly: Boolean,
    lineNumbers: Boolean,
    fontSizeSp: Float,
    lineSpacingExtra: Int,
    monospace: Boolean,
    modifier: Modifier = Modifier,
    gotoLine: Int? = null,
    onGotoConsumed: () -> Unit = {},
    gotoOffset: Int? = null,
    onGotoOffsetConsumed: () -> Unit = {},
) {
    val holder = remember { EditorBinding() }
    holder.callback = onTextChange

    // Disable syntax highlighting for very large buffers (keeps the editor responsive).
    val effectiveScope = scopeName?.takeIf { text.length <= LARGE_FILE_CHARS }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            CodeEditor(context).also { editor ->
                editor.isLineNumberEnabled = lineNumbers
                holder.appliedLineNumbers = lineNumbers
                editor.setTextSize(fontSizeSp)
                holder.appliedFontSize = fontSizeSp
                val densityPx = context.resources.displayMetrics.density
                editor.setLineSpacingExtra(lineSpacingExtra * densityPx)
                holder.appliedSpacing = lineSpacingExtra
                editor.setTypefaceText(
                    if (monospace) android.graphics.Typeface.MONOSPACE else android.graphics.Typeface.DEFAULT,
                )
                holder.appliedMono = monospace
                editor.setText(text)
                holder.lastKnownText = text
                editor.subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                    val current = editor.text.toString()
                    holder.lastKnownText = current
                    holder.callback?.invoke(current)
                }
                applyTheme(editor, themeId)
                applyLanguage(editor, effectiveScope)
                holder.appliedScope = effectiveScope
                holder.appliedTheme = themeId
                controller.bind(editor)
                editor.setWordwrap(wordWrap)
                holder.appliedWrap = wordWrap
                editor.setEditable(!readOnly)
                holder.appliedReadOnly = readOnly
            }
        },
        update = { editor ->
            if (text != holder.lastKnownText) {
                holder.lastKnownText = text
                editor.setText(text)
            }
            if (themeId != holder.appliedTheme) {
                applyTheme(editor, themeId)
                holder.appliedTheme = themeId
            }
            if (effectiveScope != holder.appliedScope) {
                applyLanguage(editor, effectiveScope)
                holder.appliedScope = effectiveScope
            }
            if (wordWrap != holder.appliedWrap) {
                editor.setWordwrap(wordWrap)
                holder.appliedWrap = wordWrap
            }
            if (readOnly != holder.appliedReadOnly) {
                editor.setEditable(!readOnly)
                holder.appliedReadOnly = readOnly
            }
            if (lineNumbers != holder.appliedLineNumbers) {
                editor.isLineNumberEnabled = lineNumbers
                holder.appliedLineNumbers = lineNumbers
            }
            if (fontSizeSp != holder.appliedFontSize) {
                editor.setTextSize(fontSizeSp)
                holder.appliedFontSize = fontSizeSp
            }
            if (lineSpacingExtra != holder.appliedSpacing) {
                val densityPx = editor.context.resources.displayMetrics.density
                editor.setLineSpacingExtra(lineSpacingExtra * densityPx)
                holder.appliedSpacing = lineSpacingExtra
            }
            if (monospace != holder.appliedMono) {
                editor.setTypefaceText(
                    if (monospace) android.graphics.Typeface.MONOSPACE else android.graphics.Typeface.DEFAULT,
                )
                holder.appliedMono = monospace
            }
            if (gotoLine != null) {
                // Runs AFTER setText above, so the jump lands on the freshly loaded document.
                // Mirrors CodeEditorController.goToLine: 1-based line, clamped to the line range.
                val target = (gotoLine - 1).coerceIn(0, (editor.lineCount - 1).coerceAtLeast(0))
                editor.setSelection(target, 0)
                editor.ensureSelectionVisible()
                onGotoConsumed()
            }
            if (gotoOffset != null) {
                // Restore a remembered caret offset after the document text is in place.
                val pos = editor.text.indexer.getCharPosition(gotoOffset.coerceIn(0, editor.text.length))
                editor.setSelection(pos.line, pos.column)
                editor.ensureSelectionVisible()
                onGotoOffsetConsumed()
            }
        },
        onRelease = { editor ->
            controller.unbind()
            editor.release()
        },
    )
}

private fun applyTheme(editor: CodeEditor, themeId: String) {
    // Themes are registered lazily; make sure this one is loaded before selecting it.
    tools.lunaris.texteditor.syntax.TextMateBootstrap.ensureTheme(themeId)
    val registry = ThemeRegistry.getInstance()
    runCatching { registry.setTheme(themeId) }
        .onFailure { Log.w("CodeEditorView", "Failed to set theme '$themeId'", it) }
    editor.colorScheme = TextMateColorScheme.create(registry)
}

private fun applyLanguage(editor: CodeEditor, scopeName: String?) {
    editor.setEditorLanguage(
        if (scopeName == null) EmptyLanguage()
        else TextMateLanguage.create(scopeName, true),
    )
}

private class EditorBinding {
    var callback: ((String) -> Unit)? = null
    var lastKnownText: String = ""
    var appliedScope: String? = null
    var appliedTheme: String? = null
    var appliedWrap: Boolean? = null
    var appliedReadOnly: Boolean? = null
    var appliedLineNumbers: Boolean? = null
    var appliedFontSize: Float? = null
    var appliedSpacing: Int? = null
    var appliedMono: Boolean? = null
}
