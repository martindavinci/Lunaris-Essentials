package tools.lunaris.texteditor.data

import kotlinx.coroutines.flow.Flow

data class PersistedTab(
    val id: String,
    val uri: String?,
    val title: String,
    val text: String,
    val dirty: Boolean,
    val charsetName: String,
    val lineEnding: String,
    val syntaxId: String?,
)

data class PersistedSession(val activeId: String, val tabs: List<PersistedTab>)

interface SessionStore {
    fun session(): Flow<PersistedSession>
    suspend fun save(session: PersistedSession)
    suspend fun clear()
}
