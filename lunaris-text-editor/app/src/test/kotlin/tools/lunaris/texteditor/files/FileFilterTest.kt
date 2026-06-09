package tools.lunaris.texteditor.files

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FileFilterTest {
    private fun e(name: String, dir: Boolean) = FileEntry(name, "u/$name", dir, name.substringAfterLast('.', ""))
    private val known = setOf("kt", "txt")

    @Test fun disabled_keepsEverything() {
        assertThat(FileFilter.apply(listOf(e("a.kt", false), e("b.bin", false), e("dir", true)), known, enabled = false)).hasSize(3)
    }
    @Test fun enabled_keepsKnownFilesAndAllDirectories() {
        val input = listOf(e("a.kt", false), e("b.bin", false), e("dir", true), e("c.txt", false))
        assertThat(FileFilter.apply(input, known, enabled = true).map { it.name }).containsExactly("a.kt", "dir", "c.txt")
    }
    @Test fun enabled_extensionMatchIsCaseInsensitive() {
        assertThat(FileFilter.apply(listOf(e("A.KT", false)), known, enabled = true)).hasSize(1)
    }
}
