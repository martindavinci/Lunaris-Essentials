package io.github.martindavinci.lunaris.archives.browse

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.testing.entry
import org.junit.Test

class EntrySortTest {
    private fun row(name: String, folder: Boolean = false, size: Long = 0, mtime: Long? = 0) =
        FolderRow(name, listOf(name), folder, if (folder) null else entry(0, name, size = size, mtime = mtime), 0, size)
    private val rows = listOf(row("b.txt", size = 5, mtime = 3), row("docs", folder = true), row("a10.md", size = 50, mtime = 1), row("a2.md", size = 1, mtime = 2))
    private fun names(order: SortOrder, input: List<FolderRow> = rows) = EntrySort.sort(input, order).map { it.name }

    @Test fun defaultIsNameWithFoldersFirst() {
        assertThat(names(SortOrder())).containsExactly("docs", "a2.md", "a10.md", "b.txt").inOrder()
    }
    @Test fun bySizeDescending() {
        assertThat(names(SortOrder(SortField.SIZE, ascending = false))).containsExactly("docs", "a10.md", "b.txt", "a2.md").inOrder()
    }
    @Test fun byDate() {
        assertThat(names(SortOrder(SortField.DATE))).containsExactly("docs", "a10.md", "a2.md", "b.txt").inOrder()
    }
    @Test fun byTypeThenName() {
        assertThat(names(SortOrder(SortField.TYPE))).containsExactly("docs", "a2.md", "a10.md", "b.txt").inOrder()
    }
    @Test fun foldersMixedWhenAsked() {
        assertThat(names(SortOrder(foldersFirst = false))).containsExactly("a2.md", "a10.md", "b.txt", "docs").inOrder()
    }
    @Test fun descendingName_reversesTheNames() {
        assertThat(names(SortOrder(ascending = false))).containsExactly("docs", "b.txt", "a10.md", "a2.md").inOrder()
    }
    @Test fun unknownDates_goLast() {
        val withNull = rows + row("c.txt", mtime = null)
        assertThat(names(SortOrder(SortField.DATE), withNull).last()).isEqualTo("c.txt")
        assertThat(names(SortOrder(SortField.DATE, ascending = false), withNull).last()).isEqualTo("c.txt")
    }
    @Test fun equalFields_fallBackToNameAscending() {
        val same = listOf(row("b", size = 1), row("a", size = 1))
        assertThat(names(SortOrder(SortField.SIZE, ascending = false), same)).containsExactly("a", "b").inOrder()
    }
}
