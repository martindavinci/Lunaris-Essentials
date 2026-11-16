package io.github.martindavinci.lunaris.archives.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.martindavinci.lunaris.archives.browse.SortField
import io.github.martindavinci.lunaris.archives.browse.SortOrder
import io.github.martindavinci.lunaris.archives.feature.browser.BrowserPreferences
import io.github.martindavinci.lunaris.archives.recents.RecentsStore
import io.github.martindavinci.lunaris.archives.recents.SettingsSource
import io.github.martindavinci.lunaris.archives.recents.UriPermissions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Theme, recents on or off, and the sort of the browser. Turning recents off forgets them and their permissions. */
@Singleton
class SettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val recents: RecentsStore,
    private val permissions: UriPermissions,
) : SettingsSource, BrowserPreferences {
    override val settings: Flow<AppSettings> = dataStore.dataOrEmpty().map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            theme = ThemeMode.entries.firstOrNull { it.name == prefs[THEME] } ?: defaults.theme,
            rememberRecents = prefs[REMEMBER_RECENTS] ?: defaults.rememberRecents,
            sort = prefs[SORT]?.let(::decodeSort) ?: defaults.sort,
        )
    }

    suspend fun setTheme(theme: ThemeMode) {
        dataStore.editIfPossible { it[THEME] = theme.name }
    }

    suspend fun setRememberRecents(on: Boolean) {
        dataStore.editIfPossible { it[REMEMBER_RECENTS] = on }
        if (!on) recents.clear().forEach { permissions.release(it.uri) }
    }

    override suspend fun saveSort(order: SortOrder) {
        dataStore.editIfPossible { it[SORT] = encodeSort(order) }
    }

    /** "NAME:1:1": field, ascending, folders first. */
    private fun encodeSort(order: SortOrder) = listOf(order.field.name, order.ascending.flag(), order.foldersFirst.flag()).joinToString(SEPARATOR)

    private fun decodeSort(text: String): SortOrder? {
        val parts = text.split(SEPARATOR)
        val field = SortField.entries.firstOrNull { it.name == parts.getOrNull(0) } ?: return null
        return SortOrder(field, parts.getOrNull(1) == ON, parts.getOrNull(2) == ON)
    }

    private fun Boolean.flag() = if (this) ON else OFF

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val REMEMBER_RECENTS = booleanPreferencesKey("remember_recents")
        val SORT = stringPreferencesKey("sort")
        const val SEPARATOR = ":"
        const val ON = "1"
        const val OFF = "0"
    }
}
