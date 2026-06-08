package tools.lunaris.texteditor.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "editor_prefs")

@Singleton
class EditorPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) : ColorSchemeStore {

    private val colorSchemeKey = stringPreferencesKey("color_scheme_id")

    override fun colorSchemeId(default: String): Flow<String> =
        context.dataStore.data.map { it[colorSchemeKey] ?: default }

    override suspend fun setColorSchemeId(id: String) {
        context.dataStore.edit { it[colorSchemeKey] = id }
    }
}
