package io.github.martindavinci.lunaris.pdf.tts

import kotlin.math.roundToInt

/**
 * One sentence being spoken: [index] of [total] on its [page]. [occurrence] counts the same words earlier
 * on the page, to tell them apart.
 */
data class SpokenSentence(val page: Int, val text: String, val index: Int, val total: Int, val occurrence: Int = 0)

/** The speeds offered: what the slider gives, in tenths. */
object SpeechRates {
    const val NORMAL = 1f
    const val MIN = 0.5f
    const val MAX = 4f
    private const val STEPS_IN_ONE = 10f

    fun snap(rate: Float): Float = (rate.coerceIn(MIN, MAX) * STEPS_IN_ONE).roundToInt() / STEPS_IN_ONE
}

sealed interface ReadAloudState {
    data object Idle : ReadAloudState
    data class Reading(val page: Int) : ReadAloudState
    data class Paused(val page: Int) : ReadAloudState

    /** The pages have no text to read, which usually means a scanned document. */
    data class NoText(val page: Int) : ReadAloudState

    /** This phone has no usable text to speech. */
    data object Unavailable : ReadAloudState
}
