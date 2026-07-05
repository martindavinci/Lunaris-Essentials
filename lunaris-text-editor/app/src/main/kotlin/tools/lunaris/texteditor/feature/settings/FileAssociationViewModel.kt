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

    val rows: StateFlow<List<AssocRow>> =
        store.associations()
            .map { assoc ->
                assoc.entries.sortedBy { it.first }.map { (ext, langId) ->
                    AssocRow(ext, langId, catalog.findById(langId)?.display ?: langId)
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Real languages plus plaintext, for the picker ("Testo semplice" = no highlighting). */
    val pickerLanguages: List<LanguageDefinition> = catalog.languages

    fun setLanguage(extension: String, languageId: String) = add(extension, languageId)

    fun add(extension: String, languageId: String) {
        if (extension.isBlank()) return
        viewModelScope.launch { store.put(extension.trim().removePrefix("."), languageId) }
    }

    fun remove(extension: String) { viewModelScope.launch { store.removeAssociation(extension) } }
}

data class AssocRow(val ext: String, val langId: String, val display: String)
