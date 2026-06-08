package tools.lunaris.texteditor.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tools.lunaris.texteditor.file.FileRef
import tools.lunaris.texteditor.file.FileRepository
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repository: FileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    /** Text last loaded from or saved to disk; used to compute the dirty flag. */
    private var savedText: String = ""

    fun onContentChanged(text: String) {
        _state.update { it.copy(text = text, dirty = text != savedText) }
    }

    fun newFile() {
        savedText = ""
        _state.value = EditorState()
    }

    fun load(ref: FileRef) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val loaded = repository.load(ref)
                savedText = loaded.text
                _state.value = EditorState(
                    title = loaded.name ?: "untitled",
                    text = loaded.text,
                    fileRef = loaded.ref,
                    charset = loaded.charset,
                    lineEnding = loaded.lineEnding,
                    dirty = false,
                )
            } catch (t: Throwable) {
                _state.update { it.copy(isLoading = false, error = t.message) }
            }
        }
    }

    fun save(ref: FileRef) {
        val current = _state.value
        viewModelScope.launch {
            try {
                repository.save(ref, current.text, current.charset, current.lineEnding)
                savedText = current.text
                _state.update {
                    it.copy(fileRef = ref, dirty = false, savedSignal = it.savedSignal + 1, error = null)
                }
            } catch (t: Throwable) {
                _state.update { it.copy(error = t.message) }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }
}
