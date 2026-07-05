package tools.lunaris.texteditor.data

import kotlinx.coroutines.flow.Flow

data class RecentEntry(val uri: String, val name: String, val timestamp: Long = 0L)

interface RecentsStore {
    fun recents(): Flow<List<RecentEntry>>
    suspend fun touch(entry: RecentEntry)
    suspend fun remove(uri: String)
    /** Keeps only the recents for which [keep] returns true (drops unreachable/deleted ones). */
    suspend fun prune(keep: (String) -> Boolean)
}
