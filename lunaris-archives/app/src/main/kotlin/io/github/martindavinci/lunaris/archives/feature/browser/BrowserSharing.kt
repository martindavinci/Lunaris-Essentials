package io.github.martindavinci.lunaris.archives.feature.browser

import io.github.martindavinci.lunaris.archives.archive.ArchiveEntry
import io.github.martindavinci.lunaris.archives.browse.EntryMime
import io.github.martindavinci.lunaris.archives.files.CopyResult
import io.github.martindavinci.lunaris.archives.files.EntryOpener
import io.github.martindavinci.lunaris.archives.session.ArchiveSession
import io.github.martindavinci.lunaris.archives.session.OpenArchive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** "Share": the chosen files are copied one by one and handed to the system share sheet together. */
internal class BrowserSharing(
    private val scope: CoroutineScope,
    private val session: ArchiveSession,
    private val opener: EntryOpener,
    private val state: MutableStateFlow<BrowserUiState>,
) {
    fun share(archive: OpenArchive, entries: List<ArchiveEntry>) {
        if (entries.isEmpty()) return
        scope.launch {
            val ready = ArrayList<CopyResult.Ready>()
            for (entry in entries) {
                val password = session.passwordFor(archive.id)
                val result = try {
                    opener.copy(archive, entry, password) {}
                } finally {
                    password?.fill(WIPED)
                }
                if (result !is CopyResult.Ready) {
                    state.update { it.copy(notice = BrowserNotice.ShareFailed) }
                    return@launch
                }
                ready += result
            }
            state.update { it.copy(launch = LaunchRequest.Send(ready.map { r -> r.uri }, commonMime(ready.map { r -> r.mime }))) }
        }
    }

    /** The selected files (a selection with folders cannot be shared), then the selection ends. */
    fun shareSelection(archive: OpenArchive) {
        val wanted = state.value.selection.orEmpty()
        share(archive, archive.index.entries.filter { it.index in wanted })
        state.update(BrowserContent::clearSelection)
    }

    /** The archive file itself, as the app received it. */
    fun shareArchive(archive: OpenArchive) {
        val mime = EntryMime.of(archive.source.name, null) { null }
        state.update { it.copy(launch = LaunchRequest.Send(listOf(archive.source.uri), mime)) }
    }

    /** One type when all agree, "image/ *" when only the family does, else anything. */
    private fun commonMime(mimes: List<String>): String {
        val distinct = mimes.distinct()
        if (distinct.size == 1) return distinct.single()
        val families = distinct.map { it.substringBefore('/') }.distinct()
        return if (families.size == 1) "${families.single()}/$ANY" else "$ANY/$ANY"
    }

    private companion object {
        const val ANY = "*"
        val WIPED = Char(0)
    }
}
