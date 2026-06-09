package tools.lunaris.texteditor.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tools.lunaris.texteditor.data.AssociationStore
import tools.lunaris.texteditor.syntax.LanguageCatalog
import tools.lunaris.texteditor.syntax.LanguageDefinition
import javax.inject.Inject

@HiltViewModel
class FileAssociationViewModel @Inject constructor(
    private val store: AssociationStore,
    val catalog: LanguageCatalog,
) : ViewModel() {

    /** ext -> language display name, sorted by extension. */
    val rows: StateFlow<List<Pair<String, String>>> =
        store.associations()
            .map { assoc ->
                assoc.entries.sortedBy { it.first }.map { (ext, langId) ->
                    ext to (catalog.findById(langId)?.display ?: langId)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val languages: List<LanguageDefinition> get() = catalog.languages.filter { it.scopeName != null }

    fun add(extension: String, languageId: String) {
        if (extension.isBlank()) return
        viewModelScope.launch { store.put(extension.trim().removePrefix("."), languageId) }
    }

    fun remove(extension: String) { viewModelScope.launch { store.removeAssociation(extension) } }
}
