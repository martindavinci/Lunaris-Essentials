package io.github.martindavinci.lunaris.pdf.feature.reader

import io.github.martindavinci.lunaris.pdf.viewer.ViewerChannel
import io.github.martindavinci.lunaris.pdf.viewer.ViewerCommand
import io.github.martindavinci.lunaris.pdf.viewer.ViewerEvent

/** Search for the Reader. It runs as the query is typed, from [MIN_QUERY_LENGTH] letters on. */
internal class ReaderSearch(
    private val viewer: ViewerChannel,
    private val state: () -> ReaderUiState,
    private val update: ((ReaderUiState) -> ReaderUiState) -> Unit,
) {
    fun open() = update { it.copy(search = SearchState(startPage = it.page), chromeVisible = true) }

    /** A search that moved far leaves the way back to where it started. */
    fun close() {
        update { current ->
            val start = current.search?.startPage ?: current.page
            current.copy(search = null, jump = PageJump.after(current.jump, start, current.page))
        }
        viewer.send(ViewerCommand.CloseFind)
    }

    fun submit(query: String) {
        change { it.copy(query = query) }
        if (query.isLongEnough()) {
            find(previous = false)
        } else {
            change { it.copy(current = 0, total = 0, page = 0, onPage = 0, searching = false) }
            viewer.send(ViewerCommand.CloseFind)
        }
    }

    fun toggleMatchCase() {
        change { it.copy(caseSensitive = !it.caseSensitive) }
        find(previous = false)
    }

    fun toggleWholeWords() {
        change { it.copy(wholeWords = !it.wholeWords) }
        find(previous = false)
    }

    fun find(previous: Boolean) {
        val search = state().search ?: return
        if (!search.query.isLongEnough()) return
        change { it.copy(searching = true) }
        viewer.send(ViewerCommand.Find(search.query, previous, search.caseSensitive, search.wholeWords))
    }

    fun onResult(event: ViewerEvent.FindResult) = change {
        it.copy(
            current = event.current,
            total = event.total,
            searching = !event.done,
            page = event.page,
            onPage = event.onPage,
        )
    }

    private fun change(transform: (SearchState) -> SearchState) = update { current ->
        current.copy(search = current.search?.let(transform))
    }

    private fun String.isLongEnough(): Boolean = trim().length >= MIN_QUERY_LENGTH

    private companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}
