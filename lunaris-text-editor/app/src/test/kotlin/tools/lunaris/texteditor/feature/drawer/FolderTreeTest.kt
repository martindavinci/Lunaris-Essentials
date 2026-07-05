package tools.lunaris.texteditor.feature.drawer

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tools.lunaris.texteditor.files.FileEntry

class FolderTreeTest {
    private fun dir(ref: String, name: String) = FileEntry(name, ref, true, "")
    private fun file(ref: String, name: String) = FileEntry(name, ref, false, name.substringAfterLast('.', ""))

    @Test fun collapsedRoots_flattenToRootsOnly() {
        val t = FolderTree(listOf(RootRef("r1", "web"), RootRef("r2", "notes")), emptySet(), emptyMap())
        assertThat(t.flatten().map { it.ref }).containsExactly("r1", "r2").inOrder()
        assertThat(t.flatten().all { it.depth == 0 }).isTrue()
    }

    @Test fun expandedRoot_showsChildrenIndented() {
        val t = FolderTree(listOf(RootRef("r1", "web")), setOf("r1"),
            mapOf("r1" to listOf(dir("r1/sub", "sub"), file("r1/a.js", "a.js"))))
        val n = t.flatten()
        assertThat(n.map { it.ref }).containsExactly("r1", "r1/sub", "r1/a.js").inOrder()
        assertThat(n[1].depth).isEqualTo(1)
        assertThat(n[0].childCount).isEqualTo(2)
    }

    @Test fun nestedExpansion_recurses() {
        val t = FolderTree(
            listOf(RootRef("r1", "web")),
            setOf("r1", "r1/sub"),
            mapOf(
                "r1" to listOf(dir("r1/sub", "sub")),
                "r1/sub" to listOf(file("r1/sub/x.md", "x.md")),
            ),
        )
        assertThat(t.flatten().map { it.ref }).containsExactly("r1", "r1/sub", "r1/sub/x.md").inOrder()
        assertThat(t.flatten().last().depth).isEqualTo(2)
    }

    @Test fun toggle_flipsExpanded() {
        val t = FolderTree(listOf(RootRef("r1", "web")), emptySet(), emptyMap())
        assertThat(t.toggle("r1").expanded).containsExactly("r1")
        assertThat(t.toggle("r1").toggle("r1").expanded).isEmpty()
    }

    @Test fun expandedButNoChildrenLoaded_showsNoChildren_countNull() {
        val t = FolderTree(listOf(RootRef("r1", "web")), setOf("r1"), emptyMap())
        assertThat(t.flatten().map { it.ref }).containsExactly("r1")
        assertThat(t.flatten().single().childCount).isNull()
    }
}
