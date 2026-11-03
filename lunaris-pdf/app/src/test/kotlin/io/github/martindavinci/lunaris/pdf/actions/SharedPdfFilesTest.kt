package io.github.martindavinci.lunaris.pdf.actions

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SharedPdfFilesTest {
    @get:Rule val folder = TemporaryFolder()

    private fun target(name: String) = SharedPdfFiles.target(folder.root, name, folderName = "abc")

    @Test fun sharedFile_keepsADisplayNameAndCannotEscapeTheCache() {
        val root = File(folder.root, "share").canonicalPath
        listOf("../x.pdf", "..\\..\\x.pdf", "/etc/x.pdf", "a/../../x.pdf").forEach { name ->
            val file = target(name)
            assertThat(file.canonicalPath).startsWith(root + File.separator)
            assertThat(file.name).isEqualTo("x.pdf")
        }
    }

    @Test fun name_keepsItsExtensionOrGetsOne() {
        assertThat(target("Report 2026.pdf").name).isEqualTo("Report 2026.pdf")
        assertThat(target("Report").name).isEqualTo("Report.pdf")
        assertThat(target("REPORT.PDF").name).isEqualTo("REPORT.PDF")
    }

    @Test fun emptyOrDotsOnlyName_getsAPlainOne() {
        listOf("", "   ", "..", ".", "/").forEach { assertThat(target(it).name).isEqualTo("document.pdf") }
    }

    @Test fun controlCharacters_areLeftOut() {
        assertThat(target("a\u0000b\nc.pdf").name).isEqualTo("abc.pdf")
    }

    @Test fun eachShare_isInAFolderOfItsOwn() {
        assertThat(target("x.pdf").parentFile?.name).isEqualTo("abc")
    }

    @Test fun oldShares_areRemovedAndNewOnesStay() {
        val old = SharedPdfFiles.target(folder.root, "old.pdf", "old").apply {
            parentFile?.mkdirs()
            writeText("x")
            parentFile?.setLastModified(1_000)
        }
        val recent = SharedPdfFiles.target(folder.root, "new.pdf", "new").apply {
            parentFile?.mkdirs()
            writeText("x")
            parentFile?.setLastModified(SharedPdfFiles.KEEP_MILLIS + 1_000)
        }
        SharedPdfFiles.removeOld(folder.root, nowMillis = SharedPdfFiles.KEEP_MILLIS + 2_000)
        assertThat(old.exists()).isFalse()
        assertThat(recent.exists()).isTrue()
    }

    @Test fun removeOld_withNothingThere_isFine() {
        SharedPdfFiles.removeOld(File(folder.root, "missing"), nowMillis = 1)
    }
}
