package tools.lunaris.texteditor.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula

/**
 * Embeds the Sora [CodeEditor] in Compose.
 *
 * @param text pushed into the editor only when it differs from the editor's current
 *        content (prevents cursor-reset feedback loops).
 * @param onTextChange called whenever the user edits, with the full new text.
 */
@Composable
fun CodeEditorView(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val callbackHolder = remember { TextChangeHolder() }
    callbackHolder.callback = onTextChange

    AndroidView(
        modifier = modifier,
        factory = { context ->
            CodeEditor(context).also { editor ->
                editor.colorScheme = SchemeDarcula()
                editor.isLineNumberEnabled = true
                editor.setText(text)
                editor.subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                    callbackHolder.callback?.invoke(editor.text.toString())
                }
            }
        },
        update = { editor ->
            if (editor.text.toString() != text) {
                editor.setText(text)
            }
        },
        onRelease = { editor -> editor.release() },
    )
}

/** Mutable holder so the latest callback is used without recreating the editor. */
private class TextChangeHolder {
    var callback: ((String) -> Unit)? = null
}
