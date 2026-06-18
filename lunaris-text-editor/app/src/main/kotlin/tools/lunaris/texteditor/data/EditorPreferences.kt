package tools.lunaris.texteditor.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarConfig
import tools.lunaris.texteditor.settings.EditorSettings
import tools.lunaris.texteditor.settings.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "editor_prefs")

@Singleton
class EditorPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) : ColorSchemeStore, RecentsStore, SessionStore, ToolbarStore, AssociationStore, SettingsStore, FileFilterStore {

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
        context.dataStore.edit { it[RECENTS_KEY] = RecentFiles.toJson(RecentFiles.remove(RecentFiles.fromJson(it[RECENTS_KEY] ?: ""), uri)) }
    }

    override suspend fun removeAssociation(extension: String) {
        context.dataStore.edit { it[ASSOC_KEY] = Associations.fromJson(it[ASSOC_KEY] ?: "").remove(extension).toJson() }
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

    // AssociationStore
    override fun associations(): Flow<Associations> =
        context.dataStore.data.map { Associations.fromJson(it[ASSOC_KEY] ?: "") }

    override suspend fun put(extension: String, languageId: String) {
        context.dataStore.edit { it[ASSOC_KEY] = Associations.fromJson(it[ASSOC_KEY] ?: "").put(extension, languageId).toJson() }
    }

    // SettingsStore
    override fun settings(): Flow<EditorSettings> =
        context.dataStore.data.map { EditorSettings.fromJson(it[SETTINGS_KEY] ?: "") }

    override suspend fun update(settings: EditorSettings) {
        context.dataStore.edit { it[SETTINGS_KEY] = settings.toJson() }
    }

    // FileFilterStore
    override fun fileFilter(): Flow<Boolean> = context.dataStore.data.map { it[FILE_FILTER_KEY] ?: true }

    override suspend fun setFileFilter(on: Boolean) { context.dataStore.edit { it[FILE_FILTER_KEY] = on } }

    private companion object {
        val COLOR_SCHEME_KEY = stringPreferencesKey("color_scheme_id")
        val RECENTS_KEY = stringPreferencesKey("recents_json")
        val SESSION_KEY = stringPreferencesKey("session_json")
        val TOOLBAR_KEY = stringPreferencesKey("toolbar_json")
        val ASSOC_KEY = stringPreferencesKey("assoc_json")
        val FILE_FILTER_KEY = booleanPreferencesKey("file_filter")
        val SETTINGS_KEY = stringPreferencesKey("settings_json")
    }
}
