package tools.lunaris.texteditor.feature.editor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tools.lunaris.texteditor.data.FileMemoryStore
import java.util.concurrent.ConcurrentHashMap

/**
 * Last caret offset per file uri: answered from memory within a session (so a quick switch back
 * never races the disk write) and persisted to [store] so it survives app restarts.
 */
class RememberedCursors(private val store: FileMemoryStore) {
    private val cache = ConcurrentHashMap<String, Int>()

    fun remember(uri: String, offset: Int, scope: CoroutineScope) {
        cache[uri] = offset
        scope.launch { runCatching { store.rememberCursor(uri, offset) } }
    }

    suspend fun offsetFor(uri: String): Int? =
        cache[uri] ?: runCatching { store.memory().first()[uri]?.cursorOffset }.getOrNull()
}
