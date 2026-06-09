package tools.lunaris.texteditor.files

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FileSorterTest {
    private fun e(name: String, dir: Boolean) = FileEntry(name, "u/$name", dir, name.substringAfterLast('.', ""))

    @Test fun directoriesBeforeFiles_thenCaseInsensitiveName() {
        val input = listOf(e("b.txt", false), e("Apple", true), e("a.txt", false), e("zeta", true))
        assertThat(FileSorter.sort(input).map { it.name }).containsExactly("Apple", "zeta", "a.txt", "b.txt").inOrder()
    }
}
