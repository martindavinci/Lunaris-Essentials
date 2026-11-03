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
class RecentsStore @Inject constructor(private val dataStore: DataStore<Preferences>) {

    val entries: Flow<List<RecentEntry>> = dataStore.dataOrEmpty()
        .map { it[ENTRIES].orEmpty() }
        .distinctUntilChanged()
        .map(RecentsCodec::fromJson)
        .flowOn(Dispatchers.Default)

    suspend fun touch(uri: String, name: String) {
        update { RecentsList.touch(it, uri, name, System.currentTimeMillis()) }
    }

    suspend fun savePosition(uri: String, page: Int, scale: String) {
        update { RecentsList.savePosition(it, uri, page, scale) }
    }

    suspend fun remove(uri: String) {
        update { RecentsList.remove(it, uri) }
    }

    suspend fun clear() {
        dataStore.editIfPossible { it.remove(ENTRIES) }
    }

    private suspend fun update(transform: (List<RecentEntry>) -> List<RecentEntry>) {
        dataStore.editIfPossible { prefs ->
            prefs[ENTRIES] = RecentsCodec.toJson(transform(RecentsCodec.fromJson(prefs[ENTRIES].orEmpty())))
        }
    }

    private companion object {
        val ENTRIES = stringPreferencesKey("recent_documents")
    }
}
