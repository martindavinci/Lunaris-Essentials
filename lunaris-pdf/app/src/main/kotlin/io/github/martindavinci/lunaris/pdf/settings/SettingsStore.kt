package io.github.martindavinci.lunaris.pdf.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.martindavinci.lunaris.pdf.tts.SpeechRates
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SettingsStore @Inject constructor(private val dataStore: DataStore<Preferences>) : SpeechPreferences {

    val settings: Flow<AppSettings> = dataStore.dataOrEmpty().map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            theme = ThemeMode.entries.firstOrNull { it.name == prefs[THEME] } ?: defaults.theme,
            nightPages = prefs[NIGHT_PAGES] ?: defaults.nightPages,
            topButton = prefs[TOP_BUTTON] ?: defaults.topButton,
        )
    }

    suspend fun setTheme(theme: ThemeMode) {
        dataStore.editIfPossible { it[THEME] = theme.name }
    }

    suspend fun setNightPages(on: Boolean) {
        dataStore.editIfPossible { it[NIGHT_PAGES] = on }
    }

    suspend fun setTopButton(on: Boolean) {
        dataStore.editIfPossible { it[TOP_BUTTON] = on }
    }

    override val speechRate: Flow<Float> = dataStore.dataOrEmpty().map { it[SPEECH_RATE] ?: SpeechRates.NORMAL }

    override val speechVoice: Flow<String?> = dataStore.dataOrEmpty().map { it[SPEECH_VOICE] }

    override suspend fun setSpeechRate(rate: Float) {
        dataStore.editIfPossible { it[SPEECH_RATE] = rate }
    }

    override suspend fun setSpeechVoice(voiceId: String?) {
        dataStore.editIfPossible { prefs ->
            if (voiceId == null) prefs.remove(SPEECH_VOICE) else prefs[SPEECH_VOICE] = voiceId
        }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val NIGHT_PAGES = booleanPreferencesKey("night_pages")
        val TOP_BUTTON = booleanPreferencesKey("top_button")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val SPEECH_VOICE = stringPreferencesKey("speech_voice")
    }
}
