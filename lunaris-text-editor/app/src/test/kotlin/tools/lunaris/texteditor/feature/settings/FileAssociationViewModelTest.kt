package tools.lunaris.texteditor.feature.settings

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import tools.lunaris.texteditor.data.FakeAssociationStore
import tools.lunaris.texteditor.syntax.LanguageCatalog
import tools.lunaris.texteditor.syntax.LanguageDefinition
import tools.lunaris.texteditor.util.MainDispatcherRule

class FileAssociationViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()
    private val catalog = LanguageCatalog(listOf(
        LanguageDefinition("plaintext", "Plain text", null, null, listOf("txt")),
        LanguageDefinition("markdown", "Markdown", "text.md", "g.json", listOf("md")),
    ))
    private val store = FakeAssociationStore()
    private fun vm() = FileAssociationViewModel(store, catalog)

    @Test fun rows_carryLangIdAndDisplay() = runTest {
        val vm = vm()
        vm.setLanguage("md", "markdown"); advanceUntilIdle()
        assertThat(vm.rows.value).containsExactly(AssocRow("md", "markdown", "Markdown"))
    }

    @Test fun setLanguage_overwritesExisting() = runTest {
        val vm = vm()
        vm.setLanguage("md", "markdown"); advanceUntilIdle()
        vm.setLanguage("md", "plaintext"); advanceUntilIdle()
        assertThat(vm.rows.value).containsExactly(AssocRow("md", "plaintext", "Plain text"))
    }

    @Test fun pickerLanguages_includePlaintext() {
        assertThat(vm().pickerLanguages.map { it.id }).containsExactly("plaintext", "markdown")
    }
}
