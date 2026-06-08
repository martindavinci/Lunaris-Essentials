package tools.lunaris.texteditor.data

import kotlinx.coroutines.flow.Flow

data class RecentEntry(val uri: String, val name: String)

interface RecentsStore {
    fun recents(): Flow<List<RecentEntry>>
    suspend fun touch(entry: RecentEntry)
    suspend fun remove(uri: String)
}
