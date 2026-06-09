package tools.lunaris.texteditor.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeBookmarksStore : BookmarksStore {
    val flow = MutableStateFlow<List<Bookmark>>(emptyList())
    override fun bookmarks(): Flow<List<Bookmark>> = flow
    override suspend fun add(bookmark: Bookmark) { flow.value = Bookmarks.add(flow.value, bookmark) }
    override suspend fun remove(bookmark: Bookmark) { flow.value = Bookmarks.remove(flow.value, bookmark) }
}
