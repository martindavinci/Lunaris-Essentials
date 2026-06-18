package tools.lunaris.texteditor.document

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OpenDocumentsTest {

    private fun untitled(id: String) = DocumentSession(id = id, title = "untitled")

    @Test
    fun newUntitled_addsActiveTab() {
        val docs = OpenDocuments.initial("a")
        assertThat(docs.sessions.map { it.id }).containsExactly("a")
        assertThat(docs.activeId).isEqualTo("a")
        assertThat(docs.active?.title).isEqualTo("untitled")
    }

    @Test
    fun opened_addsTabAndActivates() {
        val docs = OpenDocuments.initial("a").opened(untitled("b").copy(title = "b.kt"))
        assertThat(docs.sessions.map { it.id }).containsExactly("a", "b")
        assertThat(docs.activeId).isEqualTo("b")
        assertThat(docs.active?.title).isEqualTo("b.kt")
    }

    @Test
    fun openedReplacingScratch_dropsBlankLaunchTab() {
        val opened = OpenDocuments.initial("scratch")
            .openedReplacingScratch(untitled("file").copy(title = "a.kt", fileRef = tools.lunaris.texteditor.file.FileRef("content://a.kt")))
        assertThat(opened.sessions.map { it.id }).containsExactly("file")
        assertThat(opened.activeId).isEqualTo("file")
    }

    @Test
    fun openedReplacingScratch_keepsEditedAndFileTabs() {
        val edited = untitled("edited").copy(text = "wip")
        val file = untitled("file1").copy(title = "a.kt", fileRef = tools.lunaris.texteditor.file.FileRef("content://a.kt"))
        val docs = OpenDocuments(listOf(untitled("scratch"), edited, file), "scratch")
        val opened = docs.openedReplacingScratch(
            untitled("file2").copy(title = "b.kt", fileRef = tools.lunaris.texteditor.file.FileRef("content://b.kt"))
        )
        assertThat(opened.sessions.map { it.id }).containsExactly("edited", "file1", "file2")
        assertThat(opened.activeId).isEqualTo("file2")
    }

    @Test
    fun activated_switchesActive() {
        val docs = OpenDocuments.initial("a").opened(untitled("b")).activated("a")
        assertThat(docs.activeId).isEqualTo("a")
    }

    @Test
    fun updateActive_appliesTransform() {
        val docs = OpenDocuments.initial("a").updateActive { it.copy(text = "hello") }
        assertThat(docs.active?.text).isEqualTo("hello")
        assertThat(docs.active?.dirty).isTrue()
    }

    @Test
    fun closed_removesTab_andPicksNeighbourActive() {
        val docs = OpenDocuments.initial("a").opened(untitled("b")).opened(untitled("c")).activated("b")
        val after = docs.closed("b")
        assertThat(after.sessions.map { it.id }).containsExactly("a", "c")
        assertThat(after.activeId).isAnyOf("a", "c")
    }

    @Test
    fun closingLastTab_leavesOneFreshUntitled() {
        val docs = OpenDocuments.initial("a")
        val after = docs.closed("a", freshId = "z")
        assertThat(after.sessions.map { it.id }).containsExactly("z")
        assertThat(after.active?.title).isEqualTo("untitled")
        assertThat(after.active?.dirty).isFalse()
    }

    @Test
    fun dirty_isTextVsSavedText() {
        val clean = DocumentSession("a", "f", text = "x", savedText = "x")
        val dirty = DocumentSession("a", "f", text = "y", savedText = "x")
        assertThat(clean.dirty).isFalse()
        assertThat(dirty.dirty).isTrue()
    }

    @Test
    fun markSaved_clearsDirty() {
        val s = DocumentSession("a", "f", text = "y", savedText = "x").markSaved()
        assertThat(s.dirty).isFalse()
        assertThat(s.savedText).isEqualTo("y")
    }
}
