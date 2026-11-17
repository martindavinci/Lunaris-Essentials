package io.github.martindavinci.lunaris.archives.edits

import io.github.martindavinci.lunaris.archives.di.FilesDir
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

sealed interface RecoveryOutcome {
    data class Restored(val name: String, val archiveUri: String) : RecoveryOutcome
    data class RestoreFailed(val name: String, val backupPath: String) : RecoveryOutcome
}

/**
 * filesDir/update/<id>/: the backup of an archive being replaced and journal.json, written to disk before the
 * archive is touched. A journal still "replacing" at start-up means the app was closed halfway: the archive is
 * put back from the backup. A backup is deleted only once its journal says the archive is whole again.
 */
@Singleton
class UpdateJournal @Inject constructor(@param:FilesDir private val filesDir: File) {
    private val _outcomes = MutableStateFlow<List<RecoveryOutcome>>(emptyList())
    val outcomes: StateFlow<List<RecoveryOutcome>> = _outcomes.asStateFlow()

    fun folder(id: String) = File(File(filesDir, ROOT), id)

    fun begin(
        id: String, archiveUri: String, archiveName: String, backup: File, backupSize: Long, backupFingerprint: String,
        newSize: Long, newFingerprint: String,
    ) {
        write(id, JSONObject().apply {
            put(URI, archiveUri); put(NAME, archiveName); put(BACKUP, backup.path); put(BACKUP_SIZE, backupSize)
            put(BACKUP_PRINT, backupFingerprint); put(NEW_SIZE, newSize); put(NEW_PRINT, newFingerprint); put(PHASE, REPLACING)
        })
    }

    fun restoreFailed(id: String) = setPhase(id, RESTORE_FAILED)

    /** The archive is whole: the journal and everything beside it go. */
    fun done(id: String) {
        folder(id).deleteRecursively()
    }

    /** At start-up: interrupted replacements are undone, finished or orphan folders removed. */
    fun recover(io: UpdateIo): List<RecoveryOutcome> {
        val outcomes = File(filesDir, ROOT).listFiles().orEmpty().mapNotNull { dir -> recoverOne(dir, io) }
        _outcomes.value = outcomes
        return outcomes
    }

    fun shown() {
        _outcomes.value = emptyList()
    }

    private fun recoverOne(dir: File, io: UpdateIo): RecoveryOutcome? {
        val journal = runCatching { JSONObject(File(dir, JOURNAL).readText()) }.getOrNull()
        if (journal == null) {
            dir.deleteRecursively()   // no journal: the archive was never touched
            return null
        }
        val uri = journal.getString(URI)
        val name = journal.getString(NAME)
        val backup = File(journal.getString(BACKUP))
        val now = io.facts(uri)
        if (now != null && now.size == journal.getLong(NEW_SIZE) && now.fingerprint == journal.getString(NEW_PRINT)) {
            dir.deleteRecursively()   // the new archive was written whole before the app closed
            return null
        }
        val whole = backup.isFile && backup.length() == journal.getLong(BACKUP_SIZE) &&
            RandomAccessFile(backup, "r").use { Fingerprint.of(it.channel, backup.length()) } == journal.getString(BACKUP_PRINT)
        val restored = whole && runCatching { Replace.copy(backup, uri, io) }.getOrDefault(false)
        if (!restored) {
            setPhase(dir.name, RESTORE_FAILED)
            return RecoveryOutcome.RestoreFailed(name, backup.path)
        }
        dir.deleteRecursively()
        return RecoveryOutcome.Restored(name, uri)
    }

    private fun setPhase(id: String, phase: String) {
        val current = runCatching { JSONObject(File(folder(id), JOURNAL).readText()) }.getOrNull() ?: return
        write(id, current.put(PHASE, phase))
    }

    private fun write(id: String, json: JSONObject) {
        val dir = folder(id).apply { mkdirs() }
        val temp = File(dir, JOURNAL + TEMP)
        FileOutputStream(temp).use { out ->
            out.write(json.toString().toByteArray())
            out.fd.sync()
        }
        val target = File(dir, JOURNAL)
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
    }

    private companion object {
        const val ROOT = "update"
        const val JOURNAL = "journal.json"
        const val TEMP = ".new"
        const val URI = "uri"
        const val NAME = "name"
        const val BACKUP = "backup"
        const val BACKUP_SIZE = "backupSize"
        const val BACKUP_PRINT = "backupFingerprint"
        const val NEW_SIZE = "newSize"
        const val NEW_PRINT = "newFingerprint"
        const val PHASE = "phase"
        const val REPLACING = "replacing"
        const val RESTORE_FAILED = "restore-failed"
    }
}
