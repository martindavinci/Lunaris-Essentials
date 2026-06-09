package tools.lunaris.texteditor.data

import kotlinx.coroutines.flow.Flow

interface BookmarksStore {
    fun bookmarks(): Flow<List<Bookmark>>
    suspend fun add(bookmark: Bookmark)
    suspend fun remove(bookmark: Bookmark)
}
