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
        journal.begin("u1", "file:${archive.path}", "a.zip", backup, backup.length(), print(backup), newSize = 99, newFingerprint = "new", access = Access.TAKEN)
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
        assertThat(journal.recover(io).single()).isInstanceOf(RecoveryOutcome.Completed::class.java)
        assertThat(journal.outcomes.value).isEmpty()   // nothing to tell the user
        assertThat(archive.readText()).isEqualTo("new bytes"); assertThat(folder.exists()).isFalse()
    }
    @Test fun restoreFails_keepsBackupAndJournal() {
        val (_, backup) = interrupted()
        io.alwaysFail = true
        val outcome = journal.recover(io).single() as RecoveryOutcome.RestoreFailed
        assertThat(outcome.backupPath).isEqualTo(backup.path); assertThat(backup.exists()).isTrue()
        io.alwaysFail = false
        assertThat(journal.recover(io).single()).isInstanceOf(RecoveryOutcome.RestoreFailed::class.java)   // the user decides now
        assertThat(backup.exists()).isTrue()
    }
    @Test fun damagedBackup_isNeverWrittenOverTheOriginal() {
        val (archive, backup) = interrupted()
        backup.writeBytes(ByteArray(3))
        assertThat(journal.recover(io).single()).isInstanceOf(RecoveryOutcome.RestoreFailed::class.java)
        assertThat(archive.readBytes()).isEqualTo(ByteArray(5))
    }
    @Test fun aDamagedJournal_keepsItsBackup_andDoesNotThrow() {
        val folder = journal.folder("u9").apply { mkdirs() }
        val backup = File(folder, "backup.zip").apply { writeBytes(original) }
        File(folder, "journal.json").writeText("{\"uri\": 1")
        assertThat(journal.recover(io).single()).isInstanceOf(RecoveryOutcome.RestoreFailed::class.java)
        assertThat(backup.exists()).isTrue()
    }
    @Test fun aJournalOnlyInItsTempFile_isStillRecovered() {
        val (archive, _) = interrupted()
        val dir = journal.folder("u1")
        File(dir, "journal.json").renameTo(File(dir, "journal.json.new"))
        assertThat(journal.recover(io).single()).isInstanceOf(RecoveryOutcome.Restored::class.java)
        assertThat(archive.readBytes()).isEqualTo(original)
    }
    @Test fun aBackupThatCouldNotGoBack_waitsForTheUser_andIsNotRetried() {
        val (archive, _) = interrupted()
        io.alwaysFail = true
        journal.recover(io)
        io.alwaysFail = false
        archive.writeBytes("a file the user put there since".toByteArray())
        val again = journal.recover(io).single()
        assertThat(again).isInstanceOf(RecoveryOutcome.RestoreFailed::class.java)
        assertThat(archive.readText()).isEqualTo("a file the user put there since")
        assertThat(journal.stuck.value.map { it.id }).containsExactly("u1")
    }
    @Test fun anOriginalStillAsItWas_isLeftAlone_withoutAMessage() {
        val (archive, _) = interrupted()
        archive.writeBytes(original)   // the app died before the first byte was written
        val modified = archive.lastModified()
        assertThat(journal.recover(io)).isEmpty()
        assertThat(archive.lastModified()).isEqualTo(modified); assertThat(journal.folder("u1").exists()).isFalse()
    }
    @Test fun anUpdateThatFinishedBeforeTheAppDied_namesTheRecordsToSettle() {
        val archive = temp.newFile("a.zip").apply { writeBytes("new bytes".toByteArray()) }
        val folder = journal.folder("u2").apply { mkdirs() }
        val backup = File(folder, "backup.zip").apply { writeBytes(original) }
        journal.begin("u2", "file:${archive.path}", "a.zip", backup, backup.length(), print(backup), archive.length(), print(archive), listOf("r1", "r2"))
        val done = journal.recover(io).single() as RecoveryOutcome.Completed
        assertThat(done.recordIds).containsExactly("r1", "r2"); assertThat(done.newSize).isEqualTo(archive.length())
    }
    @Test fun theRightTakenForTheUpdate_isGivenBackAfterTheRestore() {
        val (archive, _) = interrupted()
        journal.recover(io)
        assertThat(io.released).contains("file:${archive.path}")
    }
    @Test fun aStuckBackup_canBeSavedOrDiscarded() {
        interrupted()
        io.alwaysFail = true
        journal.recover(io)
        val stuck = journal.stuck.value.single()
        journal.discard(stuck.id)
        assertThat(journal.stuck.value).isEmpty(); assertThat(journal.folder("u1").exists()).isFalse()
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
