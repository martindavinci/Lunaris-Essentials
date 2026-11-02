package io.github.martindavinci.lunaris.scanner.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsStore @Inject constructor(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = dataStore.dataOrEmpty().map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            theme = ThemeMode.entries.firstOrNull { it.name == prefs[THEME] } ?: defaults.theme,
            keepHistory = prefs[KEEP_HISTORY] ?: defaults.keepHistory,
            vibrateOnScan = prefs[VIBRATE_ON_SCAN] ?: defaults.vibrateOnScan,
        )
    }

    suspend fun setTheme(theme: ThemeMode) {
        dataStore.editIfPossible { it[THEME] = theme.name }
    }

    suspend fun setKeepHistory(keep: Boolean) {
        dataStore.editIfPossible { it[KEEP_HISTORY] = keep }
    }

    suspend fun setVibrateOnScan(vibrate: Boolean) {
        dataStore.editIfPossible { it[VIBRATE_ON_SCAN] = vibrate }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val KEEP_HISTORY = booleanPreferencesKey("keep_history")
        val VIBRATE_ON_SCAN = booleanPreferencesKey("vibrate_on_scan")
    }
}
