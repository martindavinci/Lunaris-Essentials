package tools.lunaris.texteditor.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarConfig
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "editor_prefs")

@Singleton
class EditorPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) : ColorSchemeStore, RecentsStore, SessionStore, ToolbarStore {

    override fun colorSchemeId(default: String): Flow<String> =
        context.dataStore.data.map { it[COLOR_SCHEME_KEY] ?: default }

    override suspend fun setColorSchemeId(id: String) {
        context.dataStore.edit { it[COLOR_SCHEME_KEY] = id }
    }

    // RecentsStore
    override fun recents(): Flow<List<RecentEntry>> =
        context.dataStore.data.map { RecentFiles.fromJson(it[RECENTS_KEY] ?: "") }

    override suspend fun touch(entry: RecentEntry) {
        context.dataStore.edit { prefs ->
            prefs[RECENTS_KEY] = RecentFiles.toJson(RecentFiles.touch(RecentFiles.fromJson(prefs[RECENTS_KEY] ?: ""), entry))
        }
    }

    override suspend fun remove(uri: String) {
        context.dataStore.edit { prefs ->
            prefs[RECENTS_KEY] = RecentFiles.toJson(RecentFiles.remove(RecentFiles.fromJson(prefs[RECENTS_KEY] ?: ""), uri))
        }
    }

    // SessionStore
    override fun session(): Flow<PersistedSession> =
        context.dataStore.data.map { SessionCodec.fromJson(it[SESSION_KEY] ?: "") }

    override suspend fun save(session: PersistedSession) {
        context.dataStore.edit { it[SESSION_KEY] = SessionCodec.toJson(session) }
    }

    override suspend fun clear() {
        context.dataStore.edit { it.remove(SESSION_KEY) }
    }

    // ToolbarStore
    override fun toolbar(): Flow<ToolbarConfig> =
        context.dataStore.data.map { ToolbarConfig.fromJson(it[TOOLBAR_KEY] ?: "") }

    override suspend fun setToolbar(config: ToolbarConfig) {
        context.dataStore.edit { it[TOOLBAR_KEY] = config.toJson() }
    }

    private companion object {
        val COLOR_SCHEME_KEY = stringPreferencesKey("color_scheme_id")
        val RECENTS_KEY = stringPreferencesKey("recents_json")
        val SESSION_KEY = stringPreferencesKey("session_json")
        val TOOLBAR_KEY = stringPreferencesKey("toolbar_json")
    }
}
