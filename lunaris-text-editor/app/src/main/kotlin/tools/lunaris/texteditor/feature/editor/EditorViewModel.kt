package tools.lunaris.texteditor.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tools.lunaris.texteditor.data.AssociationStore
import tools.lunaris.texteditor.data.Associations
import tools.lunaris.texteditor.data.ColorSchemeStore
import tools.lunaris.texteditor.data.FileMemoryStore
import tools.lunaris.texteditor.data.RecentEntry
import tools.lunaris.texteditor.data.RecentsStore
import tools.lunaris.texteditor.data.SessionManager
import tools.lunaris.texteditor.data.ToolbarStore
import tools.lunaris.texteditor.settings.EditorSettings
import tools.lunaris.texteditor.settings.SettingsStore
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarConfig
import tools.lunaris.texteditor.document.DocumentSession
import tools.lunaris.texteditor.document.LineEnding
import tools.lunaris.texteditor.document.OpenDocuments
import tools.lunaris.texteditor.file.FileRef
import tools.lunaris.texteditor.file.FileTooLargeException
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
    private val documentIo: DocumentIo,
    private val syntaxResolver: SyntaxResolver,
    @Named("defaultColorSchemeId") defaultColorSchemeId: String,
    val languageCatalog: LanguageCatalog,
    val colorSchemeCatalog: ColorSchemeCatalog,
    val charsetCatalog: CharsetCatalog,
    private val colorSchemeStore: ColorSchemeStore,
    private val recentsStore: RecentsStore,
    private val sessionManager: SessionManager,
    private val toolbarStore: ToolbarStore,
    private val associationStore: AssociationStore,
    private val settingsStore: SettingsStore,
    private val previewHolder: tools.lunaris.texteditor.preview.PreviewHolder,
    private val fileMemory: FileMemoryStore,
) : ViewModel() {

    private var colorSchemeId = defaultColorSchemeId
    private var associations = Associations.empty()
    private var currentSettings = EditorSettings()
    private val docs = MutableStateFlow(OpenDocuments.initial(newId()))
    // Per-uri caret offset, remembered when a file is left (closed, switched away from, app
    // stopped) so reopening it — also after a restart — lands the cursor where it was.
    private val cursors = RememberedCursors(fileMemory)
    private val _state = MutableStateFlow(EditorUiState(colorSchemeId = colorSchemeId))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    // Custom scope (not viewModelScope): the init read runs at construction time, which in unit
    // tests is BEFORE MainDispatcherRule installs the test main dispatcher. Unconfined starts the
    // read inline without requiring Dispatchers.Main. DataStore's first read still suspends, so
    // hydration can resume on DataStore's thread AFTER early actions (an open-with intent's
    // openFile, shared text) — the restore therefore merges into [docs] instead of replacing it,
    // and every [docs] write goes through update{} so the two threads can't lose each other's tabs.
    // Cancelled in onCleared().
    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    init {
        publish()
        initScope.launch {
            val storedScheme = colorSchemeStore.colorSchemeId(colorSchemeId).first()
            // A persisted id may no longer exist (e.g. a theme was removed) — fall back to default.
            colorSchemeId = if (colorSchemeCatalog.findById(storedScheme) != null) storedScheme
                else colorSchemeCatalog.defaultId
            val s = settingsStore.settings().first()
            currentSettings = s
            if (s.sessionRestore) {
                restoreSession()
                restoreActiveCursor()
            }
            val tb = toolbarStore.toolbar().first()
            _state.update { it.copy(toolbar = tb) }
            associations = associationStore.associations().first()
            publish()
        }
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private fun parseLineEnding(value: String): LineEnding = when (value) {
        "CRLF" -> LineEnding.CRLF
        "CR" -> LineEnding.CR
        else -> LineEnding.LF   // "LF" and "Auto" → LF
    }

    private fun mutate(block: (OpenDocuments) -> OpenDocuments) {
        docs.update(block)
        publish()
    }

    private fun publish() {
        val d = docs.value
        val a = d.active
        _state.update { prev ->
            prev.copy(
                tabs = d.sessions.map { TabSummary(it.id, it.title, it.dirty) },
                activeTabId = d.activeId,
                title = a?.title ?: DocumentSession.UNTITLED,
                text = a?.text ?: "",
                fileRef = a?.fileRef,
                charset = a?.charset ?: StandardCharsets.UTF_8,
                lineEnding = a?.lineEnding ?: LineEnding.LF,
                syntax = a?.syntax ?: SyntaxSelection.Auto,
                scopeName = a?.scopeName,
                dirty = a?.dirty ?: false,
                colorSchemeId = colorSchemeId,
                readOnly = a?.readOnly ?: false,
                pendingGoToLine = prev.pendingGoToLine,
            )
        }
    }

    fun onContentChanged(text: String) = mutate { it.updateActive { s -> s.copy(text = text) } }
    fun newTab() = mutate { it.opened(DocumentSession(id = newId(), lineEnding = parseLineEnding(currentSettings.defaultLineEnding))) }

    /** Opens shared text (ACTION_SEND) in a new tab. Left dirty so Save offers to write it out. */
    fun newTabWithText(text: String) = mutate {
        it.openedReplacingScratch(
            DocumentSession(id = newId(), text = text, lineEnding = parseLineEnding(currentSettings.defaultLineEnding)),
        )
    }
    /** [outgoingCursor] is the caret of the tab being left, remembered for when it's back. */
    fun switchTab(id: String, outgoingCursor: Int? = null) {
        if (id == docs.value.activeId) return
        if (outgoingCursor != null) rememberCursor(outgoingCursor)
        mutate { it.activated(id) }
        viewModelScope.launch { restoreActiveCursor() }
    }

    /** Remembers [offset] as the active file's caret (e.g. when the app goes to background). */
    fun rememberCursor(offset: Int) {
        docs.value.active?.fileRef?.uri?.let { cursors.remember(it, offset, viewModelScope) }
    }

    private suspend fun restoreActiveCursor() {
        val uri = docs.value.active?.fileRef?.uri ?: return
        cursors.offsetFor(uri)?.let { offset -> _state.update { it.copy(pendingGoToOffset = offset) } }
    }
    /** [cursorOffset] is the caret position of the closing tab (when it's the active one), kept so
     *  reopening the same file restores it. */
    fun closeTab(id: String, cursorOffset: Int? = null) {
        if (cursorOffset != null) {
            docs.value.sessions.firstOrNull { it.id == id }?.fileRef?.uri
                ?.let { cursors.remember(it, cursorOffset, viewModelScope) }
        }
        mutate { it.closed(id, freshId = newId()) }
    }

    fun openFile(ref: FileRef, gotoLine: Int? = null, readOnly: Boolean = false) {
        val existing = docs.value.sessions.firstOrNull { it.fileRef?.uri == ref.uri }
        if (existing != null) {
            docs.update { it.activated(existing.id) }
            publish()
            _state.update { it.copy(pendingGoToLine = gotoLine) }
            return
        }
        _state.update { it.copy(isLoading = true, error = null, errorKind = null) }
        viewModelScope.launch {
            try {
                val forced = currentSettings.defaultEncoding
                    .takeIf { it != "Auto" }
                    ?.let { runCatching { Charset.forName(it) }.getOrNull() }
                val tab = documentIo.openTab(ref, newId(), associations.map, forced = forced)
                // Re-check under update{}: a second open of the same file (double tap, rotation)
                // or the session restore may have added it while this load was in flight.
                docs.update { d ->
                    d.sessions.firstOrNull { it.fileRef?.uri == ref.uri }?.let { d.activated(it.id) }
                        ?: d.openedReplacingScratch(tab.copy(readOnly = readOnly))
                }
                publish()
                // Restore the remembered caret unless the caller asked for a specific line.
                val restoredOffset = if (gotoLine == null) cursors.offsetFor(ref.uri) else null
                _state.update {
                    it.copy(pendingGoToLine = gotoLine, pendingGoToOffset = restoredOffset, isLoading = false)
                }
            } catch (t: FileTooLargeException) {
                _state.update { it.copy(isLoading = false, error = null, errorKind = EditorErrorKind.TOO_LARGE) }
            } catch (t: SecurityException) {
                // No persisted grant for this uri (e.g. an open-with file from a past session):
                // prompt the user to re-pick it so a fresh permission can be taken.
                _state.update { it.copy(isLoading = false, error = null, errorKind = null, reauthorizeUri = ref.uri) }
            } catch (t: java.io.FileNotFoundException) {
                // The file is gone (moved/deleted) and can't be recovered — drop it from recents
                // so the dead entry stops showing.
                recentsStore.remove(ref.uri)
                _state.update { it.copy(isLoading = false, error = null, errorKind = EditorErrorKind.NOT_FOUND) }
            } catch (t: Throwable) {
                _state.update { it.copy(isLoading = false, error = t.message, errorKind = EditorErrorKind.OPEN) }
            }
        }
    }

    fun consumeGoToLine() = _state.update { it.copy(pendingGoToLine = null) }

    fun consumeGoToOffset() = _state.update { it.copy(pendingGoToOffset = null) }

    fun save(ref: FileRef) {
        val active = docs.value.active ?: return
        val written = active.text
        viewModelScope.launch {
            try {
                val saved = documentIo.persist(ref, written, active.charset, active.lineEnding, active.title, active.hasBom)
                docs.update { d ->
                    d.replace(active.id) {
                        it.copy(fileRef = ref, title = saved.name, savedText = written, charset = saved.charset, hasBom = saved.hasBom)
                    }
                }
                publish()
                val notice = if (saved.charsetChanged) EditorErrorKind.SAVED_AS_UTF8 else null
                _state.update { it.copy(savedSignal = it.savedSignal + 1, error = null, errorKind = notice) }
            } catch (t: SecurityException) {
                requestSaveAs()          // no write grant (e.g. a shared read-only file)
            } catch (t: java.io.FileNotFoundException) {
                requestSaveAs()          // target can't be opened for writing
            } catch (t: Throwable) {
                _state.update { it.copy(error = t.message, errorKind = EditorErrorKind.SAVE) }
            }
        }
    }

    private fun requestSaveAs() =
        _state.update { it.copy(saveAsRequest = it.saveAsRequest + 1, error = null, errorKind = null) }

    fun autosave() {
        val targets = Autosaver.tabsToAutosave(docs.value)
        if (targets.isEmpty()) return
        viewModelScope.launch {
            targets.forEach { tab ->
                val ref = tab.fileRef ?: return@forEach
                val written = tab.text
                runCatching { documentIo.persist(ref, written, tab.charset, tab.lineEnding, tab.title, tab.hasBom) }
                    .onSuccess { saved ->
                        docs.update { d ->
                            d.replace(tab.id) { it.copy(savedText = written, charset = saved.charset, hasBom = saved.hasBom) }
                        }
                    }
            }
            publish()
        }
    }

    fun setSyntax(selection: SyntaxSelection) = mutate {
        it.updateActive { s -> s.copy(syntax = selection, scopeName = syntaxResolver.resolveScope(selection, s.title, associations.map)) }
    }

    fun setColorScheme(id: String) {
        colorSchemeId = id
        publish()
        viewModelScope.launch { runCatching { colorSchemeStore.setColorSchemeId(id) } }
    }

    /** Reloads the active file from disk. Unsaved edits are only dropped once [discardChanges]
     *  confirms it; otherwise the request parks in [EditorUiState.pendingReload] for the UI. */
    fun reopenWithEncoding(charset: Charset, discardChanges: Boolean = false) {
        val active = docs.value.active ?: return
        val ref = active.fileRef ?: return
        if (active.dirty && !discardChanges) {
            _state.update { it.copy(pendingReload = charset) }
            return
        }
        _state.update { it.copy(pendingReload = null) }
        viewModelScope.launch {
            try {
                val loaded = documentIo.reload(ref, charset)
                docs.update { d ->
                    d.replace(active.id) {
                        it.copy(
                            text = loaded.text,
                            savedText = loaded.text,
                            charset = loaded.charset,
                            lineEnding = loaded.lineEnding,
                            hasBom = loaded.hasBom,
                        )
                    }
                }
                publish()
                _state.update { it.copy(error = null, errorKind = null) }
            } catch (t: Throwable) {
                _state.update { it.copy(error = t.message, errorKind = EditorErrorKind.OPEN) }
            }
        }
    }

    /** Recent searches in the active file, newest first (empty for untitled tabs). */
    val searchHistory: StateFlow<List<String>> by lazy {
        combine(state.map { it.fileRef?.uri }.distinctUntilChanged(), fileMemory.memory()) { uri, all ->
            uri?.let { all[it]?.searches }.orEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())
    }

    fun recordSearch(query: String) {
        val uri = docs.value.active?.fileRef?.uri ?: return
        viewModelScope.launch { runCatching { fileMemory.rememberSearch(uri, query) } }
    }

    fun cancelReload() = _state.update { it.copy(pendingReload = null) }

    /** Recent files, shared (one DataStore subscription) while the editor UI collects it. */
    val recents: StateFlow<List<RecentEntry>> by lazy {
        recentsStore.recents().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())
    }

    fun consumeError() = _state.update { it.copy(error = null, errorKind = null) }

    fun consumeReauthorize() = _state.update { it.copy(reauthorizeUri = null) }

    private suspend fun restoreSession() {
        sessionManager.restore()?.let { restored -> docs.update { it.mergedWithRestored(restored) } }
    }

    fun toggleWordWrap() { _state.update { it.copy(wordWrap = !it.wordWrap) } }
    fun setWordWrap(on: Boolean) = _state.update { it.copy(wordWrap = on) }

    fun toggleReadOnly() = mutate { it.updateActive { s -> s.copy(readOnly = !s.readOnly) } }

    fun setToolbar(config: ToolbarConfig) {
        _state.update { it.copy(toolbar = config) }
        viewModelScope.launch { runCatching { toolbarStore.setToolbar(config) } }
    }

    fun snapshotSession() {
        viewModelScope.launch { runCatching { sessionManager.save(docs.value) } }
    }

    fun previewActive() {
        val a = docs.value.active ?: return
        previewHolder.request =
            tools.lunaris.texteditor.preview.PreviewRequest(a.title, a.title, a.text, previewLanguageHint(a))
    }

    /**
     * highlight.js language token for the doc's chosen syntax (its primary extension), or null for
     * plain text. Lets an extensionless untitled file still highlight in preview per its selected
     * language instead of falling back to plain <pre>.
     */
    private fun previewLanguageHint(doc: tools.lunaris.texteditor.document.DocumentSession): String? {
        val def = when (val s = doc.syntax) {
            is tools.lunaris.texteditor.syntax.SyntaxSelection.Explicit -> languageCatalog.findById(s.languageId)
            else -> languageCatalog.findByScope(doc.scopeName)
        }
        return def?.takeIf { it.scopeName != null }?.extensions?.firstOrNull()
    }

    override fun onCleared() {
        initScope.cancel()
        super.onCleared()
    }

    private companion object {
        /** Keep upstream flows alive briefly across config changes. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
