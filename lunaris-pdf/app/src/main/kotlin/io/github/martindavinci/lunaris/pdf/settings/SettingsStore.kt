package io.github.martindavinci.lunaris.pdf.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SettingsStore @Inject constructor(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = dataStore.dataOrEmpty().map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            theme = ThemeMode.entries.firstOrNull { it.name == prefs[THEME] } ?: defaults.theme,
            nightPages = prefs[NIGHT_PAGES] ?: defaults.nightPages,
        )
    }

    suspend fun setTheme(theme: ThemeMode) {
        dataStore.editIfPossible { it[THEME] = theme.name }
    }

    suspend fun setNightPages(on: Boolean) {
        dataStore.editIfPossible { it[NIGHT_PAGES] = on }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val NIGHT_PAGES = booleanPreferencesKey("night_pages")
    }
}
