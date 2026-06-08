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
 * @param text pushed into the editor only when it differs from the last text the editor
 *        is known to hold (prevents cursor-reset feedback loops and avoids serializing
 *        the whole document on every recomposition).
 * @param onTextChange called whenever the user edits, with the full new text.
 */
@Composable
fun CodeEditorView(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val holder = remember { EditorBinding() }
    holder.callback = onTextChange

    AndroidView(
        modifier = modifier,
        factory = { context ->
            CodeEditor(context).also { editor ->
                editor.colorScheme = SchemeDarcula()
                editor.isLineNumberEnabled = true
                editor.setText(text)
                holder.lastKnownText = text
                editor.subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                    val current = editor.text.toString()
                    holder.lastKnownText = current
                    holder.callback?.invoke(current)
                }
            }
        },
        update = { editor ->
            // Compare against the cached String (not editor.text.toString()) so we don't
            // serialize the whole document on every recomposition. Only external changes
            // (load / new file) differ from lastKnownText and trigger a setText.
            if (text != holder.lastKnownText) {
                holder.lastKnownText = text
                editor.setText(text)
            }
        },
        onRelease = { editor -> editor.release() },
    )
}

/** Holds the live callback + last-known text so recomposition stays O(1). */
private class EditorBinding {
    var callback: ((String) -> Unit)? = null
    var lastKnownText: String = ""
}
