package io.github.martindavinci.lunaris.archives.feature.browser

import io.github.martindavinci.lunaris.archives.safety.Limits
import io.github.martindavinci.lunaris.archives.session.ArchiveSession
import io.github.martindavinci.lunaris.archives.session.NoSpaceException
import io.github.martindavinci.lunaris.archives.session.OpenOutcome
import io.github.martindavinci.lunaris.archives.session.OpenRequest
import io.github.martindavinci.lunaris.archives.session.SourceResult
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Opens the archive of a browser route: the open one, or the request behind the id. */
internal class BrowserLoader(
    private val session: ArchiveSession,
    private val sources: SourcesPort,
    private val state: MutableStateFlow<BrowserUiState>,
) {
    private var request: OpenRequest? = null
    private var id: String? = null

    /** Null when nothing more is to be done (the problem is already in the state). */
    suspend fun load(id: String?): OpenOutcome? {
        if (id == null) return GONE
        this.id = id
        session.get(id)?.let { return OpenOutcome.Opened(it) }
        val request = session.request(id) ?: return GONE
        this.request = request
        return withReadingHint {
            request.nestedFile?.let { path ->
                return@withReadingHint session.open(sources.fromFile(path, path.substringAfterLast('/')), request.parentId, request.nestedEntry, id)
            }
            when (val result = sources.open(request.uri)) {
                is SourceResult.Ready -> session.open(result.source, request.parentId, request.nestedEntry, id)
                SourceResult.Gone -> GONE
                is SourceResult.NeedsCopy -> OpenOutcome.NeedsCopy(request.uri, result.bytes)
            }
        }
    }

    /** For providers that cannot be read at random: one copy into the cache, then the same opening. */
    suspend fun copyAndOpen(uri: String, bytes: Long?): OpenOutcome? {
        val request = request ?: return GONE
        state.update { it.copy(loading = LoadState.Copying(0, bytes)) }
        val source = try {
            sources.copyIn(uri, bytes) { done -> state.update { it.copy(loading = LoadState.Copying(done, bytes)) } }
        } catch (_: NoSpaceException) {
            state.update { it.copy(notice = BrowserNotice.NoSpace, exit = true) }
            return null
        }
        state.update { it.copy(loading = LoadState.Reading) }
        return session.open(source, request.parentId, request.nestedEntry, id ?: return GONE)
    }

    private suspend fun withReadingHint(block: suspend () -> OpenOutcome): OpenOutcome = coroutineScope {
        val hint = launch {
            delay(Limits.READING_HINT_DELAY_MILLIS)
            state.update { if (it.loading == LoadState.Reading) it.copy(loading = LoadState.ReadingSlow(0)) else it }
        }
        try {
            block()
        } finally {
            hint.cancel()
        }
    }
}
