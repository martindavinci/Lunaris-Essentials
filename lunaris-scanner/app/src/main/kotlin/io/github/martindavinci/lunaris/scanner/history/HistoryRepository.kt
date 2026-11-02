package io.github.martindavinci.lunaris.scanner.history

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import io.github.martindavinci.lunaris.scanner.settings.SettingsStore
import io.github.martindavinci.lunaris.scanner.settings.dataOrEmpty
import io.github.martindavinci.lunaris.scanner.settings.editIfPossible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Newest first, at most [HistoryList.MAX_ENTRIES] entries. [add] does nothing when keepHistory is
 * off, and a consecutive duplicate refreshes the timestamp.
 */
@Singleton
class HistoryRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val settingsStore: SettingsStore,
) {
    val entries: Flow<List<HistoryEntry>> = dataStore.dataOrEmpty()
        .map { it[ENTRIES].orEmpty() }
        .distinctUntilChanged()
        .map(HistoryCodec::fromJson)
        .flowOn(Dispatchers.Default)

    suspend fun add(code: ScannedCode) {
        if (!settingsStore.settings.first().keepHistory) return
        update { HistoryList.add(it, code, System.currentTimeMillis()) }
    }

    suspend fun delete(id: Long) {
        update { HistoryList.delete(it, id) }
    }

    suspend fun clear() {
        dataStore.editIfPossible { it.remove(ENTRIES) }
    }

    private suspend fun update(transform: (List<HistoryEntry>) -> List<HistoryEntry>) {
        dataStore.editIfPossible { prefs ->
            prefs[ENTRIES] = HistoryCodec.toJson(transform(HistoryCodec.fromJson(prefs[ENTRIES].orEmpty())))
        }
    }

    private companion object {
        val ENTRIES = stringPreferencesKey("history_entries")
    }
}
