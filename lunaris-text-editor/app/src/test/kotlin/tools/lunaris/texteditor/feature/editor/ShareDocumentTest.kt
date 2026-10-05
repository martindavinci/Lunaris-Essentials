package tools.lunaris.texteditor.feature.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ShareDocumentTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun sanitize_replacesPathSeparators() {
        assertThat(sanitizeFileName("a/b\\c.txt")).isEqualTo("a_b_c.txt")
    }

    @Test fun sanitize_dotOnlyNames_fallBackToUntitled() {
        assertThat(sanitizeFileName(".")).isEqualTo("untitled.txt")
        assertThat(sanitizeFileName("..")).isEqualTo("untitled.txt")
        assertThat(sanitizeFileName("  ")).isEqualTo("untitled.txt")
    }

    @Test fun prepareShareDir_removesPreviousCopies() {
        val dir = File(tmp.root, "share").apply { mkdirs() }
        File(dir, "old-secret.txt").writeText("secret")
        prepareShareDir(tmp.root)
        assertThat(dir.listFiles()!!.toList()).isEmpty()
    }
}
