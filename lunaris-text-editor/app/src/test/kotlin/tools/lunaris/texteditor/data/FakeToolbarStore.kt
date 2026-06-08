package tools.lunaris.texteditor.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarConfig

class FakeToolbarStore : ToolbarStore {
    val flow = MutableStateFlow(ToolbarConfig.DEFAULT)
    override fun toolbar(): Flow<ToolbarConfig> = flow
    override suspend fun setToolbar(config: ToolbarConfig) { flow.value = config }
}
