package tools.lunaris.texteditor.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsStore(initial: EditorSettings = EditorSettings()) : SettingsStore {
    val flow = MutableStateFlow(initial)
    override fun settings(): Flow<EditorSettings> = flow
    override suspend fun update(settings: EditorSettings) { flow.value = settings }
}
