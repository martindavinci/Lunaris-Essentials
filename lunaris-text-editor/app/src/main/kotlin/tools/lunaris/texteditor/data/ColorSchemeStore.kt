package tools.lunaris.texteditor.data

import kotlinx.coroutines.flow.Flow

/** Persists the user's chosen color scheme id. */
interface ColorSchemeStore {
    fun colorSchemeId(default: String): Flow<String>
    suspend fun setColorSchemeId(id: String)
}
