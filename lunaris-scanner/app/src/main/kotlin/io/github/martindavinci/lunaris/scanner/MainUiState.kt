package io.github.martindavinci.lunaris.scanner

import io.github.martindavinci.lunaris.scanner.settings.AppSettings

/** [settings] is null until the stored settings have been read. */
data class MainUiState(
    val settings: AppSettings? = null,
    val sharedImage: SharedImageState = SharedImageState.NONE,
)

enum class SharedImageState { NONE, DECODING, FOUND, NOT_FOUND }

sealed interface MainAction {
    data class ImageShared(val uri: String) : MainAction
}
