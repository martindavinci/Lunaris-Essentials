package tools.lunaris.texteditor.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tools.lunaris.texteditor.document.DocumentSession
import tools.lunaris.texteditor.document.OpenDocuments
import tools.lunaris.texteditor.file.FileRef
import tools.lunaris.texteditor.syntax.LanguageCatalog
import tools.lunaris.texteditor.syntax.LanguageDefinition
import tools.lunaris.texteditor.syntax.SyntaxResolver
import tools.lunaris.texteditor.syntax.SyntaxSelection

class SessionManagerTest {
    private val resolver = SyntaxResolver(
        LanguageCatalog(
            listOf(
                LanguageDefinition("plaintext", "Plain text", null, null, listOf("txt")),
                LanguageDefinition("python", "Python", "source.python", "grammars/python.json", listOf("py")),
            )
        )
    )

    @Test
    fun restore_emptyStore_returnsNull() = runTest {
        assertThat(SessionManager(FakeSessionStore(), resolver).restore()).isNull()
    }

    @Test
    fun restore_mapsTabs_activeAndDirty_andAutoSyntax() = runTest {
        val store = FakeSessionStore(
            PersistedSession(
                "t2",
                listOf(
                    PersistedTab("t1", "content://1", "a.py", "code", false, "UTF-8", "LF", null),
                    PersistedTab("t2", null, "draft", "hello", true, "UTF-8", "LF", null),
                ),
            )
        )
        val docs = SessionManager(store, resolver).restore()!!
        assertThat(docs.sessions.map { it.title }).containsExactly("a.py", "draft")
        assertThat(docs.activeId).isEqualTo("t2")
        assertThat(docs.active!!.dirty).isTrue()
        assertThat(docs.sessions.first().scopeName).isEqualTo("source.python")
    }

    @Test
    fun restore_badCharsetAndEol_fallBackToDefaults() = runTest {
        val store = FakeSessionStore(
            PersistedSession("t1", listOf(PersistedTab("t1", "content://1", "a.txt", "x", false, "BOGUS", "NOPE", null)))
        )
        val docs = SessionManager(store, resolver).restore()!!
        assertThat(docs.sessions.first().charset.name()).isEqualTo("UTF-8")
        assertThat(docs.sessions.first().lineEnding.name).isEqualTo("LF")
    }

    @Test
    fun restore_unknownActive_fallsBackToFirst() = runTest {
        val store = FakeSessionStore(PersistedSession("ghost", listOf(PersistedTab("t1", null, "a", "x", false, "UTF-8", "LF", null))))
        assertThat(SessionManager(store, resolver).restore()!!.activeId).isEqualTo("t1")
    }

    @Test
    fun save_roundTripsThroughStore() = runTest {
        val store = FakeSessionStore()
        val docs = OpenDocuments(
            listOf(DocumentSession("t1", "a.py", fileRef = FileRef("content://1"), text = "x", savedText = "x", syntax = SyntaxSelection.Explicit("python"))),
            "t1",
        )
        SessionManager(store, resolver).save(docs)
        assertThat(store.saved!!.tabs.first().syntaxId).isEqualTo("python")
        assertThat(store.saved!!.tabs.first().uri).isEqualTo("content://1")
    }
}
