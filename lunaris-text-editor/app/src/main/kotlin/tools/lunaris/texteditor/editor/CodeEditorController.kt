package tools.lunaris.texteditor.editor

import io.github.rosemoe.sora.widget.CodeEditor

/**
 * Imperative bridge from Compose to the live Sora [CodeEditor]. Bound while the editor view
 * is on screen. Text edits made here still emit ContentChangeEvent, so the ViewModel's
 * onTextChange keeps the source-of-truth text in sync.
 */
class CodeEditorController {
    private var editor: CodeEditor? = null

    fun bind(e: CodeEditor) { editor = e }
    fun unbind() { editor = null }

    fun cursorOffset(): Int {
        val e = editor ?: return 0
        return e.text.indexer.getCharIndex(e.cursor.leftLine, e.cursor.leftColumn)
    }

    fun selectRange(start: Int, end: Int) {
        val e = editor ?: return
        val idx = e.text.indexer
        val s = idx.getCharPosition(start)
        val t = idx.getCharPosition(end)
        e.setSelectionRegion(s.line, s.column, t.line, t.column)
        e.ensureSelectionVisible()
    }

    fun selection(): IntRange {
        val e = editor ?: return 0..0
        val idx = e.text.indexer
        val l = idx.getCharIndex(e.cursor.leftLine, e.cursor.leftColumn)
        val r = idx.getCharIndex(e.cursor.rightLine, e.cursor.rightColumn)
        return l..r
    }

    fun currentLine(): Int = (editor?.cursor?.leftLine ?: 0) + 1

    fun goToLine(line: Int) {
        val e = editor ?: return
        val target = (line - 1).coerceIn(0, (e.lineCount - 1).coerceAtLeast(0))
        e.setSelection(target, 0)
        e.ensureSelectionVisible()
    }

    fun moveCursor(delta: Int) {
        val e = editor ?: return
        val target = (cursorOffset() + delta).coerceIn(0, e.text.length)
        val pos = e.text.indexer.getCharPosition(target)
        e.setSelection(pos.line, pos.column)
    }

    fun canUndo(): Boolean = editor?.canUndo() ?: false
    fun canRedo(): Boolean = editor?.canRedo() ?: false

    fun insertAtCursor(text: String) { editor?.insertText(text, text.length) }
    fun undo() { editor?.undo() }
    fun redo() { editor?.redo() }
    fun selectAll() { editor?.selectAll() }
    fun paste() { editor?.pasteText() }
    fun setWordWrap(enabled: Boolean) { editor?.setWordwrap(enabled) }
    fun setReadOnly(readOnly: Boolean) { editor?.setEditable(!readOnly) }
}
