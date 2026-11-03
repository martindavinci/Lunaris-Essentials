package io.github.martindavinci.lunaris.pdf.settings

import kotlinx.coroutines.flow.Flow

/** How the app reads aloud. Only for this app: the system's own speech settings are not touched. */
interface SpeechPreferences {
    val speechRate: Flow<Float>

    /** Null for the default voice of the language in use. */
    val speechVoice: Flow<String?>
    suspend fun setSpeechRate(rate: Float)
    suspend fun setSpeechVoice(voiceId: String?)
}
