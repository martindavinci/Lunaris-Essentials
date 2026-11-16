package io.github.martindavinci.lunaris.archives.create

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CreateSourcesTest {
    private val tree = object : SourceTree {
        val files = mapOf(
            "u:photo1" to Described("photo.jpg", 10, 1, isVirtual = false),
            "u:photo2" to Described("photo.jpg", 20, 2, isVirtual = false),
            "u:doc" to Described("Café.md", 3, 3, isVirtual = false),
            "u:virtual" to Described("Sheet", null, null, isVirtual = true),
        )
        val folders = mapOf(
            "root" to listOf(Child("a", "a.jpg", false, 1, 1, false), Child("sub", "sub", true, null, null, false), Child("v", "Doc", false, null, null, true)),
            "sub" to listOf(Child("b", "b.jpg", false, 2, 2, false)),
        )
        override fun describe(uri: String): Described? = files[uri]
        override fun rootOf(treeUri: String): Pair<String, String>? = if (treeUri == "tree:Holiday") "root" to "Holiday" else null
        override fun children(treeUri: String, documentId: String): List<Child> = folders[documentId].orEmpty()
        override fun uriOf(treeUri: String, documentId: String) = "doc:$documentId"
    }

    @Test fun filesKeepTheirNames_andEqualNamesAreNumbered() {
        val sources = CreateSources(tree)
        assertThat(sources.fromFiles(listOf("u:photo1", "u:photo2")).map { it.pathInZip }).containsExactly("photo.jpg", "photo (1).jpg").inOrder()
    }
    @Test fun namesAreNfc() {
        assertThat(CreateSources(tree).fromFiles(listOf("u:doc")).single().pathInZip).isEqualTo("Café.md")
    }
    @Test fun virtualAndGoneFiles_areSkippedAndListed() {
        val sources = CreateSources(tree)
        assertThat(sources.fromFiles(listOf("u:virtual", "u:gone", "u:photo1")).map { it.uri }).containsExactly("u:photo1")
        assertThat(sources.skipped).containsExactly("Sheet", "u:gone")
    }
    @Test fun aFolderIsWalkedBreadthFirstUnderItsName() {
        val sources = CreateSources(tree)
        val (name, files) = sources.fromFolder("tree:Holiday")
        assertThat(name).isEqualTo("Holiday")
        assertThat(files.map { it.pathInZip }).containsExactly("Holiday/a.jpg", "Holiday/sub/b.jpg").inOrder()
        assertThat(files.first().uri).isEqualTo("doc:a")
        assertThat(sources.skipped).containsExactly("Doc")
    }
    @Test fun anItemSumsItsFiles() {
        val item = CreateSources(tree).let { it.item(it.fromFolder("tree:Holiday").second, "Holiday", isFolder = true) }
        assertThat(item.fileCount).isEqualTo(2); assertThat(item.sizeBytes).isEqualTo(3); assertThat(item.isFolder).isTrue()
    }
    @Test fun uniquePaths_ignoreCase_andNeverHoldBackslashesOrColons() {
        assertThat(ZipPaths.unique(listOf("A.txt", "a.txt", "x:y\\z.txt"))).containsExactly("A.txt", "a (1).txt", "x_y_z.txt").inOrder()
    }
}
