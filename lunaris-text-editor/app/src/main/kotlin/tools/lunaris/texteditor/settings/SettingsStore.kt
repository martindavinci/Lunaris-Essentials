package tools.lunaris.texteditor.settings

import kotlinx.coroutines.flow.Flow

interface SettingsStore {
    fun settings(): Flow<EditorSettings>
    suspend fun update(settings: EditorSettings)
}
