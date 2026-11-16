package io.github.martindavinci.lunaris.archives.recents

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.martindavinci.lunaris.archives.settings.dataOrEmpty
import io.github.martindavinci.lunaris.archives.settings.editIfPossible
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Recent archives, newest first, in the preferences. Callers release the permissions of what goes. */
@Singleton
class RecentsStore @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val recents: Flow<List<RecentArchive>> = dataStore.dataOrEmpty()
        .map { it[RECENTS].orEmpty() }
        .distinctUntilChanged()
        .map(RecentsCodec::decode)
        .flowOn(Dispatchers.Default)

    /** Adds or moves to the top; returns what fell off the end. */
    suspend fun remember(item: RecentArchive): List<RecentArchive> {
        var evicted = emptyList<RecentArchive>()
        update { list -> RecentsHome.upsert(list, item).also { evicted = it.second }.first }
        return evicted
    }

    suspend fun remove(uri: String): RecentArchive? {
        var removed: RecentArchive? = null
        update { list -> list.filter { it.uri != uri }.also { removed = list.firstOrNull { it.uri == uri } } }
        return removed
    }

    /** Undo of a removal: back in its place by time. */
    suspend fun restore(item: RecentArchive) {
        update { list -> (list.filter { it.uri != item.uri } + item).sortedByDescending { it.lastOpenedMillis } }
    }

    suspend fun updatePath(uri: String, path: List<String>?) {
        update { list -> list.map { if (it.uri == uri) it.copy(lastPath = path) else it } }
    }

    /** Everything that was there, for releasing permissions. */
    suspend fun clear(): List<RecentArchive> {
        var all = emptyList<RecentArchive>()
        update { list -> emptyList<RecentArchive>().also { all = list } }
        return all
    }

    private suspend fun update(transform: (List<RecentArchive>) -> List<RecentArchive>) {
        dataStore.editIfPossible { prefs -> prefs[RECENTS] = RecentsCodec.encode(transform(RecentsCodec.decode(prefs[RECENTS].orEmpty()))) }
    }

    private companion object { val RECENTS = stringPreferencesKey("recent_archives") }
}
