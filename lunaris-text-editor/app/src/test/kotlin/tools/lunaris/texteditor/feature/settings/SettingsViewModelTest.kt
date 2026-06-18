package tools.lunaris.texteditor.feature.settings

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import tools.lunaris.texteditor.data.FakeAssociationStore
import tools.lunaris.texteditor.data.FakeColorSchemeStore
import tools.lunaris.texteditor.data.FakeFileFilterStore
import tools.lunaris.texteditor.data.FakeRecentsStore
import tools.lunaris.texteditor.data.FakeToolbarStore
import tools.lunaris.texteditor.data.RecentEntry
import tools.lunaris.texteditor.settings.AppTheme
import tools.lunaris.texteditor.settings.Backup
import tools.lunaris.texteditor.settings.EditorSettings
import tools.lunaris.texteditor.settings.FakeSettingsStore
import tools.lunaris.texteditor.util.MainDispatcherRule

class SettingsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val settingsStore = FakeSettingsStore()
    private val assoc = FakeAssociationStore()
    private val colors = FakeColorSchemeStore()
    private val recents = FakeRecentsStore()
    private val fileFilter = FakeFileFilterStore()
    private val toolbar = FakeToolbarStore()

    private fun newVm() = SettingsViewModel(settingsStore, assoc, colors, recents, fileFilter, toolbar)

    @Test
    fun update_persistsTransform() = runTest {
        val vm = newVm()
        vm.update { it.copy(fontSizeSp = 22) }
        advanceUntilIdle()
        assertThat(settingsStore.flow.value.fontSizeSp).isEqualTo(22)
    }

    @Test
    fun applyBackup_writesSettingsColorSchemeAndAssociations() = runTest {
        val vm = newVm()
        vm.applyBackup(
            Backup(
                settings = EditorSettings(appTheme = AppTheme.LIGHT),
                associations = mapOf("kt" to "kotlin"),
                colorSchemeId = "base16-ocean",
                recents = listOf(RecentEntry("content://1", "a.kt")),
            )
        )
        advanceUntilIdle()
        assertThat(settingsStore.flow.value.appTheme).isEqualTo(AppTheme.LIGHT)
        assertThat(colors.savedId).isEqualTo("base16-ocean")
        assertThat(assoc.flow.value.map["kt"]).isEqualTo("kotlin")
        assertThat(recents.flow.value.map { it.uri }).contains("content://1")
    }

    @Test
    fun buildBackup_collectsCurrentValues() = runTest {
        settingsStore.flow.value = EditorSettings(fontSizeSp = 19)
        assoc.put("md", "markdown")
        colors.setColorSchemeId("base16-mocha")
        recents.touch(RecentEntry("content://9", "z.kt"))
        val vm = newVm()
        val backup = vm.buildBackup()
        assertThat(backup.settings.fontSizeSp).isEqualTo(19)
        assertThat(backup.associations["md"]).isEqualTo("markdown")
        assertThat(backup.colorSchemeId).isEqualTo("base16-mocha")
        assertThat(backup.recents.map { it.uri }).contains("content://9")
    }
}
