package io.github.martindavinci.lunaris.archives.edits

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.RandomAccessFile
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateJournalTest {
    @get:Rule val temp = TemporaryFolder()
    private val io = FileUpdateIo()
    private val files by lazy { temp.newFolder("files") }
    private val journal by lazy { UpdateJournal(files) }
    private val original = "the archive as it was".toByteArray()

    private fun print(f: File) = RandomAccessFile(f, "r").use { Fingerprint.of(it.channel, f.length()) }

    /** An update interrupted after the original was emptied: what a killed process leaves. */
    private fun interrupted(): Pair<File, File> {
        val archive = temp.newFile("a.zip").apply { writeBytes(original) }
        val folder = journal.folder("u1").apply { mkdirs() }
        val backup = File(folder, "backup.zip").apply { writeBytes(original) }
        journal.begin("u1", "file:${archive.path}", "a.zip", backup, backup.length(), print(backup), newSize = 99, newFingerprint = "new")
        archive.writeBytes(ByteArray(5))   // half written
        return archive to backup
    }

    @Test fun journalLeftInReplacing_restoresBackup() {
        val (archive, _) = interrupted()
        val outcomes = journal.recover(io)
        assertThat(outcomes).containsExactly(RecoveryOutcome.Restored("a.zip", "file:${archive.path}"))
        assertThat(archive.readBytes()).isEqualTo(original)
        assertThat(journal.folder("u1").exists()).isFalse()
    }
    @Test fun originalAlreadyNew_isMarkedDone_withoutWriting() {
        val archive = temp.newFile("a.zip").apply { writeBytes("new bytes".toByteArray()) }
        val folder = journal.folder("u2").apply { mkdirs() }
        val backup = File(folder, "backup.zip").apply { writeBytes(original) }
        journal.begin("u2", "file:${archive.path}", "a.zip", backup, backup.length(), print(backup), archive.length(), print(archive))
        assertThat(journal.recover(io)).isEmpty()
        assertThat(archive.readText()).isEqualTo("new bytes"); assertThat(folder.exists()).isFalse()
    }
    @Test fun restoreFails_keepsBackupAndJournal() {
        val (_, backup) = interrupted()
        io.alwaysFail = true
        val outcome = journal.recover(io).single() as RecoveryOutcome.RestoreFailed
        assertThat(outcome.backupPath).isEqualTo(backup.path); assertThat(backup.exists()).isTrue()
        io.alwaysFail = false
        assertThat(journal.recover(io).single()).isInstanceOf(RecoveryOutcome.Restored::class.java)
    }
    @Test fun damagedBackup_isNeverWrittenOverTheOriginal() {
        val (archive, backup) = interrupted()
        backup.writeBytes(ByteArray(3))
        assertThat(journal.recover(io).single()).isInstanceOf(RecoveryOutcome.RestoreFailed::class.java)
        assertThat(archive.readBytes()).isEqualTo(ByteArray(5))
    }
    @Test fun doneJournal_andOrphanFolders_areCleanedUp() {
        val done = journal.folder("u3").apply { mkdirs(); File(this, "backup.zip").writeText("x") }
        journal.begin("u3", "file:/nowhere", "a.zip", File(done, "backup.zip"), 1, "fp", 1, "fp")
        journal.done("u3")
        val orphan = journal.folder("u4").apply { mkdirs(); File(this, "work.zip").writeText("x") }
        assertThat(journal.recover(io)).isEmpty()
        assertThat(done.exists()).isFalse(); assertThat(orphan.exists()).isFalse()
    }
    @Test fun outcomes_areKeptForTheScreens() {
        interrupted()
        journal.recover(io)
        assertThat(journal.outcomes.value).hasSize(1)
    }
}
