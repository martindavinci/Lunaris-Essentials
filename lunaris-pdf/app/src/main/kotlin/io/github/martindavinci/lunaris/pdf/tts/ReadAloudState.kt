package io.github.martindavinci.lunaris.pdf.tts

sealed interface ReadAloudState {
    data object Idle : ReadAloudState
    data class Reading(val page: Int) : ReadAloudState
    data class Paused(val page: Int) : ReadAloudState

    /** The pages have no text to read, which usually means a scanned document. */
    data class NoText(val page: Int) : ReadAloudState

    /** This phone has no usable text to speech. */
    data object Unavailable : ReadAloudState
}
