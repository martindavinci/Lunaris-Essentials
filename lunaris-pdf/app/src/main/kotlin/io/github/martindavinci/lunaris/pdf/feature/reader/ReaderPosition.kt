package io.github.martindavinci.lunaris.pdf.feature.reader

import androidx.lifecycle.SavedStateHandle
import io.github.martindavinci.lunaris.pdf.model.OpenDocument
import io.github.martindavinci.lunaris.pdf.recents.ReadingSpot
import io.github.martindavinci.lunaris.pdf.viewer.ViewerProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where reading stands: kept in [savedState] for a process restart, and in the recents once scrolling stops. */
internal class ReaderPosition(
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    private val savePosition: suspend (ReadingSpot) -> Unit,
    private val saveDelayMillis: Long,
) {
    private var saving: Job? = null

    val scale: String get() = savedState.get<String>(KEY_SCALE) ?: OpenDocument.DEFAULT_SCALE

    fun initialState(document: OpenDocument?): ReaderUiState {
        val uri = document?.uri ?: savedState.get<String>(KEY_URI)
            ?: return ReaderUiState(loading = false, error = ReaderError.CANNOT_OPEN)
        val name = document?.name ?: savedState.get<String>(KEY_NAME).orEmpty()
        savedState[KEY_URI] = uri
        savedState[KEY_NAME] = name
        if (savedState.get<String>(KEY_SCALE) == null) {
            document?.scale?.takeIf(ViewerProtocol::isScale)?.let { savedState[KEY_SCALE] = it }
        }
        val page = savedState.get<Int>(KEY_PAGE) ?: document?.page ?: 1
        return ReaderUiState(uri = uri, name = name, page = page, canWrite = document?.canWrite == true)
    }

    fun onPageChanged(uri: String?, page: Int, newScale: String, chapter: String) {
        savedState[KEY_PAGE] = page
        savedState[KEY_SCALE] = newScale
        uri ?: return
        saving?.cancel()
        saving = scope.launch {
            delay(saveDelayMillis)
            savePosition(ReadingSpot(uri, page, newScale, chapter))
        }
    }

    private companion object {
        const val KEY_URI = "uri"
        const val KEY_NAME = "name"
        const val KEY_PAGE = "page"
        const val KEY_SCALE = "scale"
    }
}
