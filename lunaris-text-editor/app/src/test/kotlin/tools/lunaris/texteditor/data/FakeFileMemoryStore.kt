package tools.lunaris.texteditor.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeFileMemoryStore : FileMemoryStore {
    val flow = MutableStateFlow<Map<String, FileMemory>>(emptyMap())
    override fun memory(): Flow<Map<String, FileMemory>> = flow
    override suspend fun rememberCursor(uri: String, offset: Int) { flow.value = FileMemoryCodec.withCursor(flow.value, uri, offset) }
    override suspend fun rememberSearch(uri: String, query: String) { flow.value = FileMemoryCodec.withSearch(flow.value, uri, query) }
}
