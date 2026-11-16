package io.github.martindavinci.lunaris.archives.extract

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.testing.FakeTree
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

/** Guards the fake's semantics, which the extraction tests rely on. */
class FakeTreeTest {
    @Test fun createReplaceDelete_roundTrip() {
        val tree = FakeTree()
        val folder = tree.ensureFolder(listOf("docs"))
        val created = tree.createFile(folder, "a.md")
        tree.open(created).use { it.write("one".toByteArray()) }
        assertThat(String(tree.read("docs", "a.md")!!)).isEqualTo("one")
        val again = tree.replace(folder, "A.MD")!!
        assertThat(again.existed).isTrue()
        tree.open(again).use { it.write("two".toByteArray()) }
        assertThat(String(tree.read("docs", "a.md")!!)).isEqualTo("two")
        tree.delete(again)
        assertThat(tree.read("docs", "a.md")).isNull()
    }
    @Test fun childNames_areLowerCasedAndKeepFoldersApart() {
        val tree = FakeTree()
        val root = tree.ensureFolder(emptyList())
        tree.ensureFolder(listOf("DCIM"))
        tree.open(tree.createFile(root, "Notes.md")).close()
        assertThat(tree.children(emptyList())).isEqualTo(ExistingNames(files = setOf("notes.md"), folders = setOf("dcim")))
        assertThat(tree.children(listOf("missing"))).isNull()
    }
    @Test fun aFolderIsNeverReturnedForReplacing() {
        val tree = FakeTree()
        tree.ensureFolder(listOf("DCIM"))
        assertThat(tree.replace(tree.ensureFolder(emptyList()), "dcim")).isNull()
    }
    @Test fun createNeverOverwrites_andMayAppendAnExtension() {
        val tree = FakeTree(appendsExtension = true)
        val root = tree.ensureFolder(emptyList())
        assertThat(tree.createFile(root, "a").name).isEqualTo("a.bin")
        assertThat(tree.createFile(root, "a").name).isNotEqualTo("a.bin")
    }
    @Test fun noSpace_isThrownAtTheRightByte() {
        val tree = FakeTree(failWithNoSpaceAfterBytes = 4)
        val out = tree.open(tree.createFile(tree.ensureFolder(emptyList()), "a"))
        out.write(ByteArray(4))
        assertThrows(IOException::class.java) { out.write(ByteArray(1)) }
    }
    @Test fun folderGone_afterTheGivenWrites() {
        val tree = FakeTree(goneAfterWrites = 1)
        val root = tree.ensureFolder(emptyList())
        tree.open(tree.createFile(root, "a")).close()
        assertThrows(FolderGoneException::class.java) { tree.open(tree.createFile(root, "b")) }
    }
}
