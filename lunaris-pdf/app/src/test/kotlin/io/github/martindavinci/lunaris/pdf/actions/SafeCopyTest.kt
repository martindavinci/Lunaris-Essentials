package io.github.martindavinci.lunaris.pdf.actions

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SafeCopyTest {
    @get:Rule val folder = TemporaryFolder()

    private var deletions = 0
    private val temp get() = File(folder.root, "work/temp.pdf")
    private val backup get() = File(folder.root, "work/backup.pdf")

    private fun save(
        source: () -> InputStream,
        target: File,
        targetHadContent: Boolean = false,
    ) = SafeCopy.save(
        temp = temp,
        backup = backup,
        openSource = source,
        openTarget = { FileOutputStream(target) },
        readTarget = { target.inputStream() },
        targetHadContent = targetHadContent,
        deleteTarget = { deletions++ },
    )

    @Test fun savingOverTheOpenFile_keepsItsContent() {
        val file = folder.newFile("x.pdf").apply { writeText("the document") }
        val saved = save(source = { file.inputStream() }, target = file, targetHadContent = true)
        assertThat(saved).isTrue()
        assertThat(file.readText()).isEqualTo("the document")
        assertThat(deletions).isEqualTo(0)
    }

    @Test fun copy_reachesTheTargetAndLeavesNoTemporaryFile() {
        val from = folder.newFile("from.pdf").apply { writeText("the document") }
        val to = folder.newFile("to.pdf")
        assertThat(save(source = { from.inputStream() }, target = to)).isTrue()
        assertThat(to.readText()).isEqualTo("the document")
        assertThat(temp.exists()).isFalse()
    }

    @Test fun unreadableSource_neverTouchesAnExistingTarget() {
        val to = folder.newFile("to.pdf").apply { writeText("already here") }
        val saved = save(source = { throw IOException("gone") }, target = to, targetHadContent = true)
        assertThat(saved).isFalse()
        assertThat(to.readText()).isEqualTo("already here")
        assertThat(deletions).isEqualTo(0)
    }

    @Test fun failedCopy_removesATargetCreatedForIt() {
        val to = folder.newFile("to.pdf")
        assertThat(save(source = { throw SecurityException("revoked") }, target = to)).isFalse()
        assertThat(deletions).isEqualTo(1)
        assertThat(temp.exists()).isFalse()
    }

    @Test fun aWriteThatBreaksHalfway_putsTheOriginalBack() {
        val from = folder.newFile("from.pdf").apply { writeText("the new document") }
        val to = folder.newFile("to.pdf").apply { writeText("the original") }
        var opened = 0
        val saved = SafeCopy.save(
            temp = temp,
            backup = backup,
            openSource = { from.inputStream() },
            openTarget = {
                opened++
                if (opened == 1) BreakingStream(FileOutputStream(to)) else FileOutputStream(to)
            },
            readTarget = { to.inputStream() },
            targetHadContent = true,
            deleteTarget = { deletions++ },
        )
        assertThat(saved).isFalse()
        assertThat(to.readText()).isEqualTo("the original")
        assertThat(deletions).isEqualTo(0)
        assertThat(backup.exists()).isFalse()
        assertThat(temp.exists()).isFalse()
    }

    @Test fun aTargetThatCannotBeRead_isStillWritten() {
        val from = folder.newFile("from.pdf").apply { writeText("the document") }
        val to = folder.newFile("to.pdf").apply { writeText("old") }
        val saved = SafeCopy.save(
            temp = temp,
            backup = backup,
            openSource = { from.inputStream() },
            openTarget = { FileOutputStream(to) },
            readTarget = { throw IOException("write only") },
            targetHadContent = true,
            deleteTarget = { deletions++ },
        )
        assertThat(saved).isTrue()
        assertThat(to.readText()).isEqualTo("the document")
    }

    /** Empties the file as a real target does, writes a little and breaks. */
    private class BreakingStream(private val out: FileOutputStream) : java.io.OutputStream() {
        override fun write(b: Int) = throw IOException("broken")
        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, 1)
            out.close()
            throw IOException("broken")
        }
    }

    @Test fun failedWrite_keepsAnExistingTargetInTheList() {
        val from = folder.newFile("from.pdf").apply { writeText("the document") }
        val saved = SafeCopy.save(
            temp = temp,
            backup = backup,
            openSource = { from.inputStream() },
            openTarget = { throw IOException("read only") },
            readTarget = { from.inputStream() },
            targetHadContent = true,
            deleteTarget = { deletions++ },
        )
        assertThat(saved).isFalse()
        assertThat(deletions).isEqualTo(0)
        assertThat(temp.exists()).isFalse()
    }
}
