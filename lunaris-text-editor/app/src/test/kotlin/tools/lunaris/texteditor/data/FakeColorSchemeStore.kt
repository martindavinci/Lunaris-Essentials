package tools.lunaris.texteditor.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeColorSchemeStore(initial: String? = null) : ColorSchemeStore {
    private val flow = MutableStateFlow(initial)
    var savedId: String? = null
        private set

    override fun colorSchemeId(default: String): Flow<String> = flow.map { it ?: default }
    override suspend fun setColorSchemeId(id: String) {
        savedId = id
        flow.value = id
    }
}
