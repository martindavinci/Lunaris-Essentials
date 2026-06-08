package tools.lunaris.texteditor.data

import kotlinx.coroutines.flow.Flow
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarConfig

interface ToolbarStore {
    fun toolbar(): Flow<ToolbarConfig>
    suspend fun setToolbar(config: ToolbarConfig)
}
