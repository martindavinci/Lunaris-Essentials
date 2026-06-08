package tools.lunaris.texteditor.feature.editor

import tools.lunaris.texteditor.document.DocumentSession
import tools.lunaris.texteditor.document.OpenDocuments

/** Policy: autosave only tabs that are file-backed AND dirty (never untitled buffers). */
object Autosaver {
    fun tabsToAutosave(docs: OpenDocuments): List<DocumentSession> =
        docs.sessions.filter { it.fileRef != null && it.dirty }
}
