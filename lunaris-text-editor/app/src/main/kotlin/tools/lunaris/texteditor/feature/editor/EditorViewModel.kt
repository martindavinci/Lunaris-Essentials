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
import tools.lunaris.texteditor.syntax.SyntaxResolver
import tools.lunaris.texteditor.syntax.SyntaxSelection
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repository: FileRepository,
    private val syntaxResolver: SyntaxResolver,
    @Named("defaultColorSchemeId") defaultColorSchemeId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState(colorSchemeId = defaultColorSchemeId))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var savedText: String = ""
    private var lastBytesRef: FileRef? = null

    fun onContentChanged(text: String) {
        _state.update { it.copy(text = text, dirty = text != savedText) }
    }

    fun newFile() {
        savedText = ""
        lastBytesRef = null
        _state.value = EditorState(colorSchemeId = _state.value.colorSchemeId)
    }

    fun load(ref: FileRef) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val loaded = repository.load(ref)
                savedText = loaded.text
                lastBytesRef = ref
                val selection = SyntaxSelection.Auto
                _state.update {
                    it.copy(
                        title = loaded.name ?: "untitled",
                        text = loaded.text,
                        fileRef = loaded.ref,
                        charset = loaded.charset,
                        lineEnding = loaded.lineEnding,
                        dirty = false,
                        isLoading = false,
                        syntax = selection,
                        scopeName = syntaxResolver.resolveScope(selection, loaded.name),
                    )
                }
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
                lastBytesRef = ref
                _state.update { it.copy(fileRef = ref, dirty = false, savedSignal = it.savedSignal + 1, error = null) }
            } catch (t: Throwable) {
                _state.update { it.copy(error = t.message) }
            }
        }
    }

    fun setSyntax(selection: SyntaxSelection) {
        _state.update { it.copy(syntax = selection, scopeName = syntaxResolver.resolveScope(selection, it.title)) }
    }

    fun setColorScheme(id: String) {
        _state.update { it.copy(colorSchemeId = id) }
    }

    fun reopenWithEncoding(charset: Charset) {
        val ref = lastBytesRef ?: return
        viewModelScope.launch {
            try {
                val loaded = repository.load(ref, forced = charset)
                savedText = loaded.text
                lastBytesRef = loaded.ref
                _state.update { it.copy(text = loaded.text, charset = loaded.charset, lineEnding = loaded.lineEnding, dirty = false) }
            } catch (t: Throwable) {
                _state.update { it.copy(error = t.message) }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }
}
