package tools.lunaris.texteditor.feature.editor

import tools.lunaris.texteditor.data.RecentEntry
import tools.lunaris.texteditor.data.RecentsStore
import tools.lunaris.texteditor.document.DocumentSession
import tools.lunaris.texteditor.document.LineEnding
import tools.lunaris.texteditor.file.FileRef
import tools.lunaris.texteditor.file.FileRepository
import tools.lunaris.texteditor.file.FileTooLargeException
import tools.lunaris.texteditor.file.LoadedDocument
import tools.lunaris.texteditor.syntax.SyntaxResolver
import tools.lunaris.texteditor.syntax.SyntaxSelection
import java.nio.charset.Charset
import javax.inject.Inject

/** File IO + tab construction for the editor. Returns values; the ViewModel applies state. */
class DocumentIo @Inject constructor(
    private val repository: FileRepository,
    private val recentsStore: RecentsStore,
    private val syntaxResolver: SyntaxResolver,
) {
    suspend fun openTab(ref: FileRef, newId: String, associations: Map<String, String>, forced: Charset? = null): DocumentSession {
        repository.size(ref)?.let { if (it > MAX_OPEN_BYTES) throw FileTooLargeException(MAX_OPEN_BYTES) }
        // Also bound the read itself: providers that don't report a size can't slip a huge file past
        // the up-front check and OOM us.
        val loaded = repository.load(ref, forced, maxBytes = MAX_OPEN_BYTES)
        val selection = SyntaxSelection.Auto
        recentsStore.touch(RecentEntry(ref.uri, loaded.name ?: DocumentSession.UNTITLED))
        return DocumentSession(
            id = newId,
            title = loaded.name ?: DocumentSession.UNTITLED,
            fileRef = loaded.ref,
            text = loaded.text,
            savedText = loaded.text,
            charset = loaded.charset,
            lineEnding = loaded.lineEnding,
            syntax = selection,
            scopeName = syntaxResolver.resolveScope(selection, loaded.name, associations),
            hasBom = loaded.hasBom,
        )
    }

    suspend fun persist(
        ref: FileRef,
        text: String,
        charset: Charset,
        lineEnding: LineEnding,
        fallbackName: String,
        hasBom: Boolean = false,
    ): String {
        repository.save(ref, text, charset, lineEnding, hasBom)
        val name = repository.displayName(ref) ?: fallbackName
        recentsStore.touch(RecentEntry(ref.uri, name))
        return name
    }

    suspend fun reload(ref: FileRef, charset: Charset): LoadedDocument =
        repository.load(ref, forced = charset, maxBytes = MAX_OPEN_BYTES)

    companion object {
        /** Hard cap on openable file size. The whole document is held in memory as a String,
         *  so anything larger risks OOM. ~12 MB covers normal text/code files comfortably. */
        const val MAX_OPEN_BYTES = 12L * 1024 * 1024
    }
}
