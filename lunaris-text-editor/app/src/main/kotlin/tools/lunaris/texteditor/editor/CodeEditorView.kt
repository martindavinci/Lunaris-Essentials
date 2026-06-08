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

@Composable
fun CodeEditorView(
    text: String,
    onTextChange: (String) -> Unit,
    scopeName: String?,
    themeId: String,
    controller: CodeEditorController,
    wordWrap: Boolean,
    modifier: Modifier = Modifier,
) {
    val holder = remember { EditorBinding() }
    holder.callback = onTextChange

    AndroidView(
        modifier = modifier,
        factory = { context ->
            CodeEditor(context).also { editor ->
                editor.isLineNumberEnabled = true
                editor.setText(text)
                holder.lastKnownText = text
                editor.subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                    val current = editor.text.toString()
                    holder.lastKnownText = current
                    holder.callback?.invoke(current)
                }
                applyTheme(editor, themeId)
                applyLanguage(editor, scopeName)
                holder.appliedScope = scopeName
                holder.appliedTheme = themeId
                controller.bind(editor)
                editor.setWordwrap(wordWrap)
                holder.appliedWrap = wordWrap
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
            if (scopeName != holder.appliedScope) {
                applyLanguage(editor, scopeName)
                holder.appliedScope = scopeName
            }
            if (wordWrap != holder.appliedWrap) {
                editor.setWordwrap(wordWrap)
                holder.appliedWrap = wordWrap
            }
        },
        onRelease = { editor ->
            controller.unbind()
            editor.release()
        },
    )
}

private fun applyTheme(editor: CodeEditor, themeId: String) {
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
}
