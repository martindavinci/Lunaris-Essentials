package tools.lunaris.texteditor.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Own DataStore file: per-file memory changes often (every tab switch / app stop) and shouldn't
// rewrite settings, recents and the session each time.
private val Context.fileMemoryStore by preferencesDataStore(name = "file_memory")

@Singleton
class FileMemoryPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) : FileMemoryStore {

    override fun memory(): Flow<Map<String, FileMemory>> =
        context.fileMemoryStore.data.map { FileMemoryCodec.fromJson(it[MEMORY_KEY].orEmpty()) }

    override suspend fun rememberCursor(uri: String, offset: Int) = update { FileMemoryCodec.withCursor(it, uri, offset) }

    override suspend fun rememberSearch(uri: String, query: String) = update { FileMemoryCodec.withSearch(it, uri, query) }

    private suspend fun update(change: (Map<String, FileMemory>) -> Map<String, FileMemory>) {
        context.fileMemoryStore.edit { prefs ->
            prefs[MEMORY_KEY] = FileMemoryCodec.toJson(change(FileMemoryCodec.fromJson(prefs[MEMORY_KEY].orEmpty())))
        }
    }

    private companion object {
        val MEMORY_KEY = stringPreferencesKey("file_memory")
    }
}
