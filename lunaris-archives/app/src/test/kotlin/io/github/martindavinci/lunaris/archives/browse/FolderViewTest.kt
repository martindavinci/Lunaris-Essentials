package io.github.martindavinci.lunaris.archives.browse

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.ArchiveEntry
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.testing.entry
import io.github.martindavinci.lunaris.archives.testing.index
import org.junit.Test

class FolderViewTest {
    private val view = FolderView(index("README.md", "docs/a.md", "docs/sub/b.md", "img/", "../evil", "/abs", "dup.txt", "dup.txt"))

    @Test fun rootListsImplicitAndDeclaredFolders() {
        val root = view.children(emptyList())!!
        assertThat(root.map { it.name }).containsExactly("README.md", "docs", "img", "dup.txt", "dup.txt")
        val docs = root.first { it.name == "docs" }
        assertThat(docs.isFolder).isTrue(); assertThat(docs.childCount).isEqualTo(2); assertThat(docs.totalSize).isEqualTo(2048)
        assertThat(docs.entry).isNull()
        val img = root.first { it.name == "img" }
        assertThat(img.childCount).isEqualTo(0)
        assertThat(img.entry?.displayPath).isEqualTo("img/")
    }
    @Test fun foldersComeBeforeFiles() {
        assertThat(view.children(emptyList())!!.map { it.isFolder }).containsExactly(true, true, false, false, false).inOrder()
    }
    @Test fun nestedFolder() {
        assertThat(view.children(listOf("docs"))!!.map { it.name }).containsExactly("a.md", "sub")
        assertThat(view.children(listOf("docs"))!!.first { it.name == "sub" }.path).containsExactly("docs", "sub").inOrder()
        assertThat(view.children(listOf("nope"))).isNull()
    }
    @Test fun unsafeEntriesAreHiddenAndCounted() { assertThat(view.hiddenUnsafe).isEqualTo(2) }
    @Test fun linksAndDevicesAreHiddenAndCounted() {
        val v = FolderView(index("ok.txt").let { it.copy(entries = it.entries + listOf(
            entry(1, "link", type = EntryType.SYMLINK), entry(2, "dev", type = EntryType.FIFO))) })
        assertThat(v.hiddenUnsafe).isEqualTo(2)
        assertThat(v.children(emptyList())!!.map { it.name }).containsExactly("ok.txt")
    }
    @Test fun entriesUnderAFolder_areEveryFileBelow() {
        assertThat(view.entriesUnder(listOf("docs")).map(ArchiveEntry::displayPath)).containsExactly("docs/a.md", "docs/sub/b.md")
        assertThat(view.entriesUnder(emptyList())).hasSize(5)
        assertThat(view.entriesUnder(listOf("nope"))).isEmpty()
    }
    @Test fun counts() { assertThat(view.fileCount()).isEqualTo(5); assertThat(view.folderCount()).isEqualTo(3) }
    @Test fun aFileAndAFolderWithTheSameName_bothShow() {
        val v = FolderView(index("x", "x/y"))
        assertThat(v.children(emptyList())!!.map { it.isFolder }).containsExactly(false, true)
    }
    @Test fun unknownSizes_countAsZero() {
        val v = FolderView(index("a/").let { it.copy(entries = it.entries + entry(1, "a/b", size = null)) })
        assertThat(v.children(emptyList())!!.single().totalSize).isEqualTo(0)
    }
}
