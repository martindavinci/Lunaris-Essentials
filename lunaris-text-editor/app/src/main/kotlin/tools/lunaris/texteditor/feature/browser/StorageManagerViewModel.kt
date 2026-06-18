package tools.lunaris.texteditor.feature.browser

import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tools.lunaris.texteditor.files.StorageAccess
import javax.inject.Inject

data class StorageUiState(val trees: List<String> = emptyList())

@HiltViewModel
class StorageManagerViewModel @Inject constructor(
    private val storageAccess: StorageAccess,
) : ViewModel() {
    private val _state = MutableStateFlow(StorageUiState())
    val state: StateFlow<StorageUiState> = _state.asStateFlow()

    fun refresh() {
        _state.value = StorageUiState(storageAccess.grantedTrees().map { it.toString() })
    }
    fun addTree(uri: Uri) { storageAccess.persistTree(uri); refresh() }
    fun release(ref: String) { storageAccess.releaseTree(Uri.parse(ref)); refresh() }
}
