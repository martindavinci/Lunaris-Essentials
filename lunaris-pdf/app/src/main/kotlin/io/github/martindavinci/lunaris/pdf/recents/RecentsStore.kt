package io.github.martindavinci.lunaris.pdf.recents

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.martindavinci.lunaris.pdf.settings.dataOrEmpty
import io.github.martindavinci.lunaris.pdf.settings.editIfPossible
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Newest first, at most [RecentsList.MAX_ENTRIES] entries. */
@Singleton
class RecentsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val thumbnails: ThumbnailStore,
) {

    val entries: Flow<List<RecentEntry>> = dataStore.dataOrEmpty()
        .map { it[ENTRIES].orEmpty() }
        .distinctUntilChanged()
        .map(RecentsCodec::fromJson)
        .flowOn(Dispatchers.Default)

    suspend fun touch(visit: RecentVisit) {
        update { RecentsList.touch(it, visit, System.currentTimeMillis()) }?.let { list ->
            thumbnails.retain(list.map { it.uri })
        }
    }

    suspend fun savePosition(spot: ReadingSpot) {
        update { RecentsList.savePosition(it, spot.uri, spot.page, spot.scale, spot.chapter) }
    }

    suspend fun remove(uri: String) {
        update { RecentsList.remove(it, uri) }
        thumbnails.remove(uri)
    }

    /** False when there are as many pins as allowed already. */
    suspend fun setPinned(uri: String, on: Boolean): Boolean {
        var done = true
        update { list ->
            RecentsList.setPinned(list, uri, on) ?: list.also { done = false }
        }
        return done
    }

    suspend fun restore(entry: RecentEntry) {
        update { RecentsList.restore(it, entry) }
    }

    suspend fun clear() {
        dataStore.editIfPossible { it.remove(ENTRIES) }
        thumbnails.clear()
    }

    /** The new list, or null if it could not be written. */
    private suspend fun update(transform: (List<RecentEntry>) -> List<RecentEntry>): List<RecentEntry>? {
        var written: List<RecentEntry>? = null
        dataStore.editIfPossible { prefs ->
            val list = transform(RecentsCodec.fromJson(prefs[ENTRIES].orEmpty()))
            prefs[ENTRIES] = RecentsCodec.toJson(list)
            written = list
        }
        return written
    }

    private companion object {
        val ENTRIES = stringPreferencesKey("recent_documents")
    }
}
