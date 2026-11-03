package io.github.martindavinci.lunaris.pdf.tts

/** One sentence being spoken: [index] of [total] on its [page]. */
data class SpokenSentence(val page: Int, val text: String, val index: Int, val total: Int)

/** The speeds offered, in the order a tap goes through them. */
object SpeechRates {
    private val RATES = listOf(1f, 1.25f, 1.5f, 0.75f)

    const val NORMAL = 1f

    fun next(rate: Float): Float = RATES.getOrElse(RATES.indexOf(rate) + 1) { RATES.first() }.takeIf { rate in RATES } ?: NORMAL
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
