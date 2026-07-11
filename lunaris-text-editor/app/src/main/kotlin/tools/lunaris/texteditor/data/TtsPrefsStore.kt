package tools.lunaris.texteditor.data

import kotlinx.coroutines.flow.Flow

/** Per-app TTS preferences. Never reflects or mutates the global/system TTS configuration. */
interface TtsPrefsStore {
    fun ttsRate(): Flow<Float>
    suspend fun setTtsRate(rate: Float)
    fun ttsVoiceId(): Flow<String?>
    suspend fun setTtsVoiceId(id: String?)
}
