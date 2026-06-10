package tools.lunaris.texteditor.feature.editor

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import tools.lunaris.texteditor.data.Associations
import tools.lunaris.texteditor.data.FakeAssociationStore
import tools.lunaris.texteditor.data.FakeBookmarksStore
import tools.lunaris.texteditor.data.FakeColorSchemeStore
import tools.lunaris.texteditor.data.FakeRecentsStore
import tools.lunaris.texteditor.data.FakeSessionStore
import tools.lunaris.texteditor.data.FakeToolbarStore
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarConfig
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarItem
import tools.lunaris.texteditor.data.PersistedSession
import tools.lunaris.texteditor.data.PersistedTab
import tools.lunaris.texteditor.data.SessionManager
import tools.lunaris.texteditor.document.EncodingCodec
import tools.lunaris.texteditor.file.FakeFileGateway
import tools.lunaris.texteditor.file.FileRef
import tools.lunaris.texteditor.file.FileRepository
import tools.lunaris.texteditor.syntax.CharsetCatalog
import tools.lunaris.texteditor.syntax.ColorSchemeCatalog
import tools.lunaris.texteditor.syntax.LanguageCatalog
import tools.lunaris.texteditor.syntax.LanguageDefinition
import tools.lunaris.texteditor.syntax.SyntaxResolver
import tools.lunaris.texteditor.syntax.SyntaxSelection
import tools.lunaris.texteditor.settings.EditorSettings
import tools.lunaris.texteditor.settings.FakeSettingsStore
import tools.lunaris.texteditor.util.MainDispatcherRule
import java.nio.charset.StandardCharsets

class EditorViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val gateway = FakeFileGateway()
    private val repo = FileRepository(gateway, EncodingCodec())
    private val catalog = LanguageCatalog(
        listOf(
            LanguageDefinition("plaintext", "Plain text", null, null, listOf("txt")),
            LanguageDefinition("python", "Python", "source.python", "grammars/python.json", listOf("py")),
        )
    )
    private val resolver = SyntaxResolver(catalog)
    private val recents = FakeRecentsStore()
    private val session = FakeSessionStore()
    private val toolbar = FakeToolbarStore()
    private val assoc = FakeAssociationStore()
    private val bookmarks = FakeBookmarksStore()
    private val settings = FakeSettingsStore()

    private val documentIo = DocumentIo(repo, recents, resolver)

    private fun newVm() = EditorViewModel(
        documentIo = documentIo,
        syntaxResolver = resolver,
        defaultColorSchemeId = "sunburst",
        languageCatalog = catalog,
        colorSchemeCatalog = ColorSchemeCatalog(emptyList(), ""),
        charsetCatalog = CharsetCatalog(),
        colorSchemeStore = FakeColorSchemeStore(),
        recentsStore = recents,
        sessionManager = SessionManager(session, resolver),
        toolbarStore = toolbar,
        associationStore = assoc,
        bookmarksStore = bookmarks,
        settingsStore = settings,
        previewHolder = tools.lunaris.texteditor.preview.PreviewHolder(),
    )

    @Test
    fun startsWithOneUntitledTab() {
        val vm = newVm()
        assertThat(vm.state.value.tabs).hasSize(1)
        assertThat(vm.state.value.title).isEqualTo("untitled")
    }

    @Test
    fun newTab_addsAndActivates() {
        val vm = newVm()
        vm.newTab()
        assertThat(vm.state.value.tabs).hasSize(2)
    }

    @Test
    fun openFile_addsTab_autoDetectsSyntax_andRecords() = runTest {
        val vm = newVm()
        val ref = FileRef("content://doc/p")
        gateway.store[ref.uri] = "print(1)".toByteArray(StandardCharsets.UTF_8)
        gateway.names[ref.uri] = "main.py"
        vm.openFile(ref)
        advanceUntilIdle()
        assertThat(vm.state.value.tabs).hasSize(2)
        assertThat(vm.state.value.title).isEqualTo("main.py")
        assertThat(vm.state.value.scopeName).isEqualTo("source.python")
        assertThat(recents.flow.value.map { it.uri }).contains(ref.uri)
    }

    @Test
    fun contentChanged_marksActiveDirty_only() {
        val vm = newVm()
        vm.newTab()
        vm.onContentChanged("edit")
        assertThat(vm.state.value.dirty).isTrue()
        vm.switchTab(vm.state.value.tabs.first().id)
        assertThat(vm.state.value.dirty).isFalse()
    }

    @Test
    fun switchTab_restoresThatTabsText() {
        val vm = newVm()
        vm.onContentChanged("first")
        val firstId = vm.state.value.activeTabId
        vm.newTab()
        vm.onContentChanged("second")
        vm.switchTab(firstId)
        assertThat(vm.state.value.text).isEqualTo("first")
    }

    @Test
    fun save_writesActive_clearsDirty_records() = runTest {
        val vm = newVm()
        vm.onContentChanged("data")
        val ref = FileRef("content://doc/s")
        vm.save(ref)
        advanceUntilIdle()
        assertThat(String(gateway.store[ref.uri]!!, StandardCharsets.UTF_8)).isEqualTo("data")
        assertThat(vm.state.value.dirty).isFalse()
        assertThat(recents.flow.value.map { it.uri }).contains(ref.uri)
    }

    @Test
    fun closeTab_removesIt() {
        val vm = newVm()
        vm.newTab()
        val id = vm.state.value.activeTabId
        vm.closeTab(id)
        assertThat(vm.state.value.tabs).hasSize(1)
    }

    @Test
    fun closingLastTab_leavesFreshUntitled() {
        val vm = newVm()
        vm.onContentChanged("x")
        vm.closeTab(vm.state.value.activeTabId)
        assertThat(vm.state.value.tabs).hasSize(1)
        assertThat(vm.state.value.dirty).isFalse()
        assertThat(vm.state.value.text).isEqualTo("")
    }

    @Test
    fun setSyntaxExplicit_updatesActiveScope() {
        val vm = newVm()
        vm.setSyntax(SyntaxSelection.Explicit("python"))
        assertThat(vm.state.value.scopeName).isEqualTo("source.python")
    }

    @Test
    fun setColorScheme_updatesState() {
        val vm = newVm()
        vm.setColorScheme("base16-tomorrow-night")
        assertThat(vm.state.value.colorSchemeId).isEqualTo("base16-tomorrow-night")
    }

    @Test
    fun reopenWithEncoding_redecodesActive() = runTest {
        val vm = newVm()
        val ref = FileRef("content://doc/e")
        gateway.store[ref.uri] = byteArrayOf(0x61, 0x62)
        gateway.names[ref.uri] = "a.txt"
        vm.openFile(ref)
        advanceUntilIdle()
        vm.reopenWithEncoding(StandardCharsets.ISO_8859_1)
        advanceUntilIdle()
        assertThat(vm.state.value.charset).isEqualTo(StandardCharsets.ISO_8859_1)
        assertThat(vm.state.value.text).isEqualTo("ab")
    }

    @Test
    fun editDuringSave_keepsDirty() = runTest {
        val vm = newVm()
        vm.onContentChanged("A")
        val ref = FileRef("content://doc/x")
        vm.save(ref)              // captures "A"; coroutine body is queued, not yet run
        vm.onContentChanged("AB") // user edits in-memory before save completes
        advanceUntilIdle()        // save body runs: writes "A", baseline becomes "A"
        assertThat(String(gateway.store[ref.uri]!!, StandardCharsets.UTF_8)).isEqualTo("A")
        assertThat(vm.state.value.text).isEqualTo("AB")
        assertThat(vm.state.value.dirty).isTrue()
    }

    @Test
    fun openFile_failure_setsErrorAndClearsLoading() = runTest {
        val vm = newVm()
        vm.openFile(FileRef("content://missing")) // no bytes in fake gateway -> readBytes throws
        advanceUntilIdle()
        assertThat(vm.state.value.error).isNotNull()
        assertThat(vm.state.value.isLoading).isFalse()
    }

    @Test
    fun restoresPersistedTabsOnInit() = runTest {
        val seeded = FakeSessionStore(
            PersistedSession(
                activeId = "t2",
                tabs = listOf(
                    PersistedTab("t1", "content://1", "a.kt", "code", false, "UTF-8", "LF", null),
                    PersistedTab("t2", null, "draft.txt", "hello", true, "UTF-8", "LF", null),
                ),
            )
        )
        settings.flow.value = EditorSettings(sessionRestore = true)
        val vm = EditorViewModel(
            DocumentIo(repo, recents, resolver), resolver, "sunburst", catalog,
            ColorSchemeCatalog(emptyList(), ""), CharsetCatalog(),
            FakeColorSchemeStore(), recents, SessionManager(seeded, resolver), toolbar,
            assoc, bookmarks, settings, tools.lunaris.texteditor.preview.PreviewHolder(),
        )
        advanceUntilIdle()
        assertThat(vm.state.value.tabs.map { it.title }).containsExactly("a.kt", "draft.txt")
        assertThat(vm.state.value.activeTabId).isEqualTo("t2")
        assertThat(vm.state.value.dirty).isTrue()
    }

    @Test
    fun doesNotRestore_whenSessionRestoreDisabled() = runTest {
        val seeded = FakeSessionStore(
            PersistedSession("t1", listOf(PersistedTab("t1", "content://1", "a.kt", "x", false, "UTF-8", "LF", null)))
        )
        settings.flow.value = EditorSettings(sessionRestore = false)
        val vm = EditorViewModel(
            DocumentIo(repo, recents, resolver), resolver, "sunburst", catalog,
            ColorSchemeCatalog(emptyList(), ""), CharsetCatalog(),
            FakeColorSchemeStore(), recents, SessionManager(seeded, resolver), toolbar,
            assoc, bookmarks, settings, tools.lunaris.texteditor.preview.PreviewHolder(),
        )
        advanceUntilIdle()
        // restore is gated off -> the single fresh untitled tab remains
        assertThat(vm.state.value.tabs).hasSize(1)
        assertThat(vm.state.value.title).isEqualTo("untitled")
    }

    @Test
    fun snapshotSession_writesAllOpenTabs() = runTest {
        val vm = newVm()
        vm.onContentChanged("draft")
        vm.newTab()
        vm.snapshotSession()
        advanceUntilIdle()
        assertThat(session.saved).isNotNull()
        assertThat(session.saved!!.tabs).hasSize(2)
    }

    @Test
    fun autosave_savesDirtyFileBackedTabs_only() = runTest {
        val vm = newVm()
        val ref = FileRef("content://doc/a")
        gateway.store[ref.uri] = "orig".toByteArray(StandardCharsets.UTF_8)
        gateway.names[ref.uri] = "a.txt"
        vm.openFile(ref)
        advanceUntilIdle()
        vm.onContentChanged("edited")  // active file tab now dirty
        vm.newTab()
        vm.onContentChanged("draft")   // untitled dirty tab (must NOT be saved)

        vm.autosave()
        advanceUntilIdle()

        assertThat(String(gateway.store[ref.uri]!!, StandardCharsets.UTF_8)).isEqualTo("edited")
        val fileTabId = vm.state.value.tabs.first { it.title == "a.txt" }.id
        vm.switchTab(fileTabId)
        assertThat(vm.state.value.dirty).isFalse()
    }

    @Test
    fun toggleWordWrap_flips() {
        val vm = newVm()
        val before = vm.state.value.wordWrap
        vm.toggleWordWrap()
        assertThat(vm.state.value.wordWrap).isEqualTo(!before)
    }

    @Test
    fun toggleReadOnly_setsActiveReadOnly() {
        val vm = newVm()
        assertThat(vm.state.value.readOnly).isFalse()
        vm.toggleReadOnly()
        assertThat(vm.state.value.readOnly).isTrue()
    }

    @Test
    fun setToolbar_persistsAndUpdatesState() = runTest {
        val vm = newVm()
        val c = ToolbarConfig.DEFAULT.toggled(ToolbarItem.UNDO, false)
        vm.setToolbar(c)
        advanceUntilIdle()
        assertThat(vm.state.value.toolbar).isEqualTo(c)
        assertThat(toolbar.flow.value).isEqualTo(c)
    }

    @Test
    fun toolbar_loadedOnInit() = runTest {
        val c = ToolbarConfig.DEFAULT.toggled(ToolbarItem.PASTE, false)
        toolbar.flow.value = c
        val vm = newVm()
        advanceUntilIdle()
        assertThat(vm.state.value.toolbar).isEqualTo(c)
    }

    @Test
    fun openFile_usesAssociationForSyntax() = runTest {
        assoc.flow.value = Associations.empty().put("txt", "python")
        val vm = newVm()
        advanceUntilIdle()
        val ref = FileRef("content://doc/n")
        gateway.store[ref.uri] = "x".toByteArray(StandardCharsets.UTF_8)
        gateway.names[ref.uri] = "notes.txt"
        vm.openFile(ref)
        advanceUntilIdle()
        assertThat(vm.state.value.scopeName).isEqualTo("source.python")
    }

    @Test
    fun addBookmark_recordsForFileBackedActive() = runTest {
        val vm = newVm()
        val ref = FileRef("content://doc/b")
        gateway.store[ref.uri] = "x".toByteArray(StandardCharsets.UTF_8)
        gateway.names[ref.uri] = "b.txt"
        vm.openFile(ref); advanceUntilIdle()
        vm.addBookmark(5); advanceUntilIdle()
        assertThat(bookmarks.flow.value.map { it.uri to it.line }).contains(ref.uri to 5)
    }

    @Test
    fun addBookmark_untitled_isNoOp() = runTest {
        val vm = newVm()
        vm.addBookmark(1); advanceUntilIdle()
        assertThat(bookmarks.flow.value).isEmpty()
    }

    @Test
    fun newTab_usesDefaultLineEndingFromSettings() = runTest {
        settings.flow.value = EditorSettings(defaultLineEnding = "CRLF")
        val vm = newVm()
        advanceUntilIdle()
        vm.newTab()
        assertThat(vm.state.value.lineEnding).isEqualTo(tools.lunaris.texteditor.document.LineEnding.CRLF)
    }

    @Test
    fun newTab_usesCrLineEnding_whenSettingIsCr() = runTest {
        settings.flow.value = EditorSettings(defaultLineEnding = "CR")
        val vm = newVm()
        advanceUntilIdle()
        vm.newTab()
        assertThat(vm.state.value.lineEnding).isEqualTo(tools.lunaris.texteditor.document.LineEnding.CR)
    }

    @Test
    fun openFile_usesDefaultEncoding_whenNotAuto() = runTest {
        settings.flow.value = EditorSettings(defaultEncoding = "ISO-8859-1")
        val vm = newVm()
        advanceUntilIdle()
        val ref = FileRef("content://doc/enc2")
        gateway.store[ref.uri] = byteArrayOf(0x61, 0x62)
        gateway.names[ref.uri] = "a.txt"
        vm.openFile(ref); advanceUntilIdle()
        assertThat(vm.state.value.charset).isEqualTo(java.nio.charset.Charset.forName("ISO-8859-1"))
    }

    @Test
    fun openFile_autoDetects_whenEncodingIsAuto() = runTest {
        settings.flow.value = EditorSettings(defaultEncoding = "Auto")
        val vm = newVm()
        advanceUntilIdle()
        val ref = FileRef("content://doc/utf8auto")
        gateway.store[ref.uri] = "ciao".toByteArray(Charsets.UTF_8)
        gateway.names[ref.uri] = "b.txt"
        vm.openFile(ref); advanceUntilIdle()
        assertThat(vm.state.value.charset).isEqualTo(Charsets.UTF_8)
    }
}
