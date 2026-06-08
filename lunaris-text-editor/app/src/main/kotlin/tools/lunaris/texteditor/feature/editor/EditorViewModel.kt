package tools.lunaris.texteditor.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tools.lunaris.texteditor.data.ColorSchemeStore
import tools.lunaris.texteditor.data.RecentEntry
import tools.lunaris.texteditor.data.RecentsStore
import tools.lunaris.texteditor.data.SessionStore
import tools.lunaris.texteditor.document.DocumentSession
import tools.lunaris.texteditor.document.LineEnding
import tools.lunaris.texteditor.document.OpenDocuments
import tools.lunaris.texteditor.file.FileRef
import tools.lunaris.texteditor.file.FileRepository
import tools.lunaris.texteditor.syntax.CharsetCatalog
import tools.lunaris.texteditor.syntax.ColorSchemeCatalog
import tools.lunaris.texteditor.syntax.LanguageCatalog
import tools.lunaris.texteditor.syntax.SyntaxResolver
import tools.lunaris.texteditor.syntax.SyntaxSelection
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repository: FileRepository,
    private val syntaxResolver: SyntaxResolver,
    @Named("defaultColorSchemeId") defaultColorSchemeId: String,
    val languageCatalog: LanguageCatalog,
    val colorSchemeCatalog: ColorSchemeCatalog,
    val charsetCatalog: CharsetCatalog,
    private val colorSchemeStore: ColorSchemeStore,
    private val recentsStore: RecentsStore,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private var colorSchemeId = defaultColorSchemeId
    private val docs = MutableStateFlow(OpenDocuments.initial(newId()))
    private val _state = MutableStateFlow(EditorUiState(colorSchemeId = colorSchemeId))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    // Custom scope (not viewModelScope): the init read runs at construction time, which in unit
    // tests is BEFORE MainDispatcherRule installs the test main dispatcher. Unconfined runs the
    // one-shot read inline without requiring Dispatchers.Main. Cancelled in onCleared().
    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    init {
        publish()
        initScope.launch {
            colorSchemeId = colorSchemeStore.colorSchemeId(colorSchemeId).first()
            publish()
        }
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private fun mutate(block: (OpenDocuments) -> OpenDocuments) {
        docs.value = block(docs.value)
        publish()
    }

    private fun publish() {
        val d = docs.value
        val a = d.active
        _state.update { prev ->
            prev.copy(
                tabs = d.sessions.map { TabSummary(it.id, it.title, it.dirty) },
                activeTabId = d.activeId,
                title = a?.title ?: "untitled",
                text = a?.text ?: "",
                fileRef = a?.fileRef,
                charset = a?.charset ?: StandardCharsets.UTF_8,
                lineEnding = a?.lineEnding ?: LineEnding.LF,
                syntax = a?.syntax ?: SyntaxSelection.Auto,
                scopeName = a?.scopeName,
                dirty = a?.dirty ?: false,
                colorSchemeId = colorSchemeId,
            )
        }
    }

    fun onContentChanged(text: String) = mutate { it.updateActive { s -> s.copy(text = text) } }
    fun newTab() = mutate { it.opened(DocumentSession(id = newId())) }
    fun switchTab(id: String) = mutate { it.activated(id) }
    fun closeTab(id: String) = mutate { it.closed(id, freshId = newId()) }

    fun openFile(ref: FileRef) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val loaded = repository.load(ref)
                val selection = SyntaxSelection.Auto
                val tab = DocumentSession(
                    id = newId(),
                    title = loaded.name ?: "untitled",
                    fileRef = loaded.ref,
                    text = loaded.text,
                    savedText = loaded.text,
                    charset = loaded.charset,
                    lineEnding = loaded.lineEnding,
                    syntax = selection,
                    scopeName = syntaxResolver.resolveScope(selection, loaded.name),
                )
                docs.value = docs.value.opened(tab)
                recentsStore.touch(RecentEntry(ref.uri, tab.title))
                publish()
                _state.update { it.copy(isLoading = false) }
            } catch (t: Throwable) {
                _state.update { it.copy(isLoading = false, error = t.message) }
            }
        }
    }

    fun save(ref: FileRef) {
        val active = docs.value.active ?: return
        val savedText = active.text
        val charset = active.charset
        val lineEnding = active.lineEnding
        val priorTitle = active.title
        viewModelScope.launch {
            try {
                repository.save(ref, savedText, charset, lineEnding)
                val name = repository.displayName(ref) ?: priorTitle
                docs.value = docs.value.replace(active.id) {
                    it.copy(fileRef = ref, title = name, savedText = savedText)
                }
                recentsStore.touch(RecentEntry(ref.uri, name))
                publish()
                _state.update { it.copy(savedSignal = it.savedSignal + 1) }
            } catch (t: Throwable) {
                _state.update { it.copy(error = t.message) }
            }
        }
    }

    fun autosave() {
        val targets = Autosaver.tabsToAutosave(docs.value)
        if (targets.isEmpty()) return
        viewModelScope.launch {
            targets.forEach { tab ->
                val ref = tab.fileRef ?: return@forEach
                val written = tab.text
                runCatching { repository.save(ref, written, tab.charset, tab.lineEnding) }
                    .onSuccess { docs.value = docs.value.replace(tab.id) { it.copy(savedText = written) } }
            }
            publish()
        }
    }

    fun setSyntax(selection: SyntaxSelection) = mutate {
        it.updateActive { s -> s.copy(syntax = selection, scopeName = syntaxResolver.resolveScope(selection, s.title)) }
    }

    fun setColorScheme(id: String) {
        colorSchemeId = id
        publish()
        viewModelScope.launch { runCatching { colorSchemeStore.setColorSchemeId(id) } }
    }

    fun reopenWithEncoding(charset: Charset) {
        val active = docs.value.active ?: return
        val ref = active.fileRef ?: return
        viewModelScope.launch {
            try {
                val loaded = repository.load(ref, forced = charset)
                docs.value = docs.value.replace(active.id) {
                    it.copy(text = loaded.text, savedText = loaded.text, charset = loaded.charset, lineEnding = loaded.lineEnding)
                }
                publish()
            } catch (t: Throwable) {
                _state.update { it.copy(error = t.message) }
            }
        }
    }

    fun recents() = recentsStore.recents()

    fun consumeError() = _state.update { it.copy(error = null) }

    override fun onCleared() {
        initScope.cancel()
        super.onCleared()
    }
}
