package tools.lunaris.texteditor.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tools.lunaris.texteditor.data.AssociationStore
import tools.lunaris.texteditor.data.ColorSchemeStore
import tools.lunaris.texteditor.data.FileFilterStore
import tools.lunaris.texteditor.data.RecentEntry
import tools.lunaris.texteditor.data.RecentsStore
import tools.lunaris.texteditor.data.ToolbarStore
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarConfig
import tools.lunaris.texteditor.settings.Backup
import tools.lunaris.texteditor.settings.EditorSettings
import tools.lunaris.texteditor.settings.SettingsStore
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val associationStore: AssociationStore,
    private val colorSchemeStore: ColorSchemeStore,
    private val recentsStore: RecentsStore,
    private val fileFilterStore: FileFilterStore,
    private val toolbarStore: ToolbarStore,
) : ViewModel() {

    val settings: StateFlow<EditorSettings> =
        settingsStore.settings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorSettings())

    fun update(transform: (EditorSettings) -> EditorSettings) {
        viewModelScope.launch { settingsStore.update(transform(settingsStore.settings().first())) }
    }

    suspend fun buildBackup(): Backup = Backup(
        settings = settingsStore.settings().first(),
        associations = associationStore.associations().first().map,
        colorSchemeId = colorSchemeStore.colorSchemeId("").first(),
        recents = recentsStore.recents().first(),
    )

    fun applyBackup(backup: Backup) {
        viewModelScope.launch {
            settingsStore.update(backup.settings)
            if (backup.colorSchemeId.isNotBlank()) colorSchemeStore.setColorSchemeId(backup.colorSchemeId)
            backup.associations.forEach { (ext, langId) -> associationStore.put(ext, langId) }
            backup.recents.forEach { recentsStore.touch(it) }
        }
    }

    fun fileFilter() = fileFilterStore.fileFilter()
    fun setFileFilter(on: Boolean) { viewModelScope.launch { fileFilterStore.setFileFilter(on) } }
    fun toolbar() = toolbarStore.toolbar()
    fun setToolbar(config: ToolbarConfig) {
        viewModelScope.launch { toolbarStore.setToolbar(config) }
    }
}
