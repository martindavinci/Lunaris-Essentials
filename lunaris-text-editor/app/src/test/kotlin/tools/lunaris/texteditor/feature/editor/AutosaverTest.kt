package tools.lunaris.texteditor.feature.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tools.lunaris.texteditor.document.DocumentSession
import tools.lunaris.texteditor.document.OpenDocuments
import tools.lunaris.texteditor.file.FileRef

class AutosaverTest {
    @Test
    fun selectsOnlyDirtyFileBackedTabs() {
        val docs = OpenDocuments(
            listOf(
                DocumentSession("a", "a.txt", fileRef = FileRef("u1"), text = "x", savedText = "x"), // clean
                DocumentSession("b", "b.txt", fileRef = FileRef("u2"), text = "y", savedText = "z"), // dirty + file
                DocumentSession("c", "untitled", fileRef = null, text = "draft", savedText = ""),     // dirty no file
            ),
            "a",
        )
        assertThat(Autosaver.tabsToAutosave(docs).map { it.id }).containsExactly("b")
    }

    @Test
    fun emptyWhenNoDirtyFiles() {
        val docs = OpenDocuments(listOf(DocumentSession("a", "a.txt", fileRef = FileRef("u1"), text = "x", savedText = "x")), "a")
        assertThat(Autosaver.tabsToAutosave(docs)).isEmpty()
    }
}
