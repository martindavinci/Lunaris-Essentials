package io.github.martindavinci.lunaris.pdf.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.martindavinci.lunaris.pdf.annotate.AnnotationHints
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SettingsStore @Inject constructor(private val dataStore: DataStore<Preferences>) : AnnotationHints {

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

    override val shown: Flow<Int> = dataStore.dataOrEmpty().map { it[SCROLL_HINTS] ?: 0 }

    override suspend fun countOne() {
        dataStore.editIfPossible { it[SCROLL_HINTS] = (it[SCROLL_HINTS] ?: 0) + 1 }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val NIGHT_PAGES = booleanPreferencesKey("night_pages")
        val TOP_BUTTON = booleanPreferencesKey("top_button")
        val SCROLL_HINTS = intPreferencesKey("annotation_scroll_hints")
    }
}
