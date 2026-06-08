package tools.lunaris.texteditor.data

import kotlinx.coroutines.flow.first
import tools.lunaris.texteditor.document.DocumentSession
import tools.lunaris.texteditor.document.LineEnding
import tools.lunaris.texteditor.document.OpenDocuments
import tools.lunaris.texteditor.file.FileRef
import tools.lunaris.texteditor.syntax.SyntaxResolver
import tools.lunaris.texteditor.syntax.SyntaxSelection
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import javax.inject.Inject

/** Restores/snapshots the set of open tabs to persistent storage. */
class SessionManager @Inject constructor(
    private val store: SessionStore,
    private val syntaxResolver: SyntaxResolver,
) {
    /** Rebuilds open tabs from storage, or null if nothing was persisted. */
    suspend fun restore(): OpenDocuments? {
        val persisted = store.session().first()
        if (persisted.tabs.isEmpty()) return null
        val sessions = persisted.tabs.map { toSession(it) }
        val activeId = if (sessions.any { it.id == persisted.activeId }) persisted.activeId else sessions.first().id
        return OpenDocuments(sessions, activeId)
    }

    suspend fun save(docs: OpenDocuments) {
        store.save(
            PersistedSession(activeId = docs.activeId, tabs = docs.sessions.map { toPersisted(it) })
        )
    }

    private fun toSession(t: PersistedTab): DocumentSession {
        val syntax = t.syntaxId?.let { SyntaxSelection.Explicit(it) } ?: SyntaxSelection.Auto
        return DocumentSession(
            id = t.id,
            title = t.title,
            fileRef = t.uri?.let { FileRef(it) },
            text = t.text,
            // NOTE: a dirty tab whose text is "" restores as clean (savedText "" == text "").
            // Accepted trade-off: the rare empty-but-dirty case loses its dirty marker on restore.
            savedText = if (t.dirty) "" else t.text,
            charset = runCatching { Charset.forName(t.charsetName) }.getOrDefault(StandardCharsets.UTF_8),
            lineEnding = runCatching { LineEnding.valueOf(t.lineEnding) }.getOrDefault(LineEnding.LF),
            syntax = syntax,
            scopeName = syntaxResolver.resolveScope(syntax, t.title),
        )
    }

    private fun toPersisted(s: DocumentSession): PersistedTab = PersistedTab(
        id = s.id,
        uri = s.fileRef?.uri,
        title = s.title,
        text = s.text,
        dirty = s.dirty,
        charsetName = s.charset.name(),
        lineEnding = s.lineEnding.name,
        syntaxId = (s.syntax as? SyntaxSelection.Explicit)?.languageId,
    )
}
