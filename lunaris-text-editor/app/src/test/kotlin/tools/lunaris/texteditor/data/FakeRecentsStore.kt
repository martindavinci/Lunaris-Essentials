package tools.lunaris.texteditor.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeRecentsStore : RecentsStore {
    val flow = MutableStateFlow<List<RecentEntry>>(emptyList())
    override fun recents(): Flow<List<RecentEntry>> = flow
    override suspend fun touch(entry: RecentEntry) { flow.value = RecentFiles.touch(flow.value, entry) }
    override suspend fun remove(uri: String) { flow.value = RecentFiles.remove(flow.value, uri) }
}
