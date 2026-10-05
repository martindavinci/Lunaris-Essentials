package tools.lunaris.texteditor.data

import kotlinx.coroutines.flow.first
import tools.lunaris.texteditor.document.DocumentSession
import tools.lunaris.texteditor.document.LineEnding
import tools.lunaris.texteditor.document.OpenDocuments
import tools.lunaris.texteditor.file.FileRef
import tools.lunaris.texteditor.file.FileRepository
import tools.lunaris.texteditor.syntax.SyntaxResolver
import tools.lunaris.texteditor.syntax.SyntaxSelection
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import javax.inject.Inject

/** Restores/snapshots the set of open tabs to persistent storage. */
class SessionManager @Inject constructor(
    private val store: SessionStore,
    private val syntaxResolver: SyntaxResolver,
    private val repository: FileRepository,
) {
    /** Rebuilds open tabs from storage, or null if nothing (restorable) was persisted. */
    suspend fun restore(): OpenDocuments? {
        val persisted = store.session().first()
        val sessions = persisted.tabs.mapNotNull { toSession(it) }
        if (sessions.isEmpty()) return null
        val activeId = if (sessions.any { it.id == persisted.activeId }) persisted.activeId else sessions.first().id
        return OpenDocuments(sessions, activeId)
    }

    suspend fun save(docs: OpenDocuments) {
        store.save(
            PersistedSession(activeId = docs.activeId, tabs = docs.sessions.map { toPersisted(it) })
        )
    }

    /** Null for a clean file-backed tab that can't be reloaded (revoked grant, file gone): it
     *  persisted no body, so restoring it would show an empty clean tab whose save wipes the file. */
    private suspend fun toSession(t: PersistedTab): DocumentSession? {
        val charset = runCatching { Charset.forName(t.charsetName) }.getOrDefault(StandardCharsets.UTF_8)
        val syntax = t.syntaxId?.let { SyntaxSelection.Explicit(it) } ?: SyntaxSelection.Auto
        val fileRef = t.uri?.let { FileRef(it) }

        // Clean, file-backed tabs persist no body — reload fresh from disk so a file changed since
        // the last session shows current content, not a stale snapshot. (BOM files re-auto-detect;
        // otherwise force the recorded charset to keep a manual encoding choice.)
        val reloaded = if (fileRef != null && !t.dirty) {
            runCatching { repository.load(fileRef, forced = if (t.hasBom) null else charset) }.getOrNull()
        } else null
        if (fileRef != null && !t.dirty && reloaded == null) return null

        val text = reloaded?.text ?: t.text
        return DocumentSession(
            id = t.id,
            title = t.title,
            fileRef = fileRef,
            text = text,
            // A dirty tab whose text is "" restores as clean (savedText "" == text ""). Accepted:
            // the rare empty-but-dirty case loses its dirty marker. Reloaded clean tabs are clean.
            savedText = if (t.dirty) "" else text,
            charset = reloaded?.charset ?: charset,
            lineEnding = reloaded?.lineEnding
                ?: runCatching { LineEnding.valueOf(t.lineEnding) }.getOrDefault(LineEnding.LF),
            syntax = syntax,
            scopeName = syntaxResolver.resolveScope(syntax, t.title),
            hasBom = reloaded?.hasBom ?: t.hasBom,
        )
    }

    private fun toPersisted(s: DocumentSession): PersistedTab = PersistedTab(
        id = s.id,
        uri = s.fileRef?.uri,
        title = s.title,
        // Only persist the body when it's the sole copy (unsaved edits, or scratch tabs with no
        // file). Clean file-backed tabs reload from disk, so storing their text just bloats DataStore.
        text = if (s.dirty || s.fileRef == null) s.text else "",
        dirty = s.dirty,
        charsetName = s.charset.name(),
        lineEnding = s.lineEnding.name,
        syntaxId = (s.syntax as? SyntaxSelection.Explicit)?.languageId,
        hasBom = s.hasBom,
    )
}
