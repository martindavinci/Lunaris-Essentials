package io.github.martindavinci.lunaris.archives.edits

import io.github.martindavinci.lunaris.archives.di.FilesDir
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

sealed interface RecoveryOutcome {
    data class Restored(val name: String, val archiveUri: String) : RecoveryOutcome
    /** The original could not be put back: its copy waits at [backupPath] for the user to save or discard it. */
    data class RestoreFailed(val id: String, val name: String, val backupPath: String) : RecoveryOutcome
    /** The new archive was written whole before the app closed: [recordIds] are the edits it carries. */
    data class Completed(val archiveUri: String, val recordIds: List<String>, val newSize: Long, val newFingerprint: String) : RecoveryOutcome
}

/**
 * filesDir/update/<id>/: the backup of an archive being replaced and journal.json, written to disk before the
 * archive is touched. A journal still "replacing" at start-up means the app was closed halfway: the archive is
 * put back from the backup. A backup that cannot go back is never retried on its own (another file may be at that
 * place by then): it waits in [stuck] for the user. A backup is deleted only when the archive is whole again.
 */
@Singleton
class UpdateJournal @Inject constructor(@param:FilesDir private val filesDir: File) {
    private val _outcomes = MutableStateFlow<List<RecoveryOutcome>>(emptyList())
    val outcomes: StateFlow<List<RecoveryOutcome>> = _outcomes.asStateFlow()
    private val _stuck = MutableStateFlow<List<RecoveryOutcome.RestoreFailed>>(emptyList())
    val stuck: StateFlow<List<RecoveryOutcome.RestoreFailed>> = _stuck.asStateFlow()

    fun folder(id: String) = File(File(filesDir, ROOT), id)

    @Suppress("LongParameterList")
    fun begin(
        id: String, archiveUri: String, archiveName: String, backup: File, backupSize: Long, backupFingerprint: String,
        newSize: Long, newFingerprint: String, recordIds: List<String> = emptyList(), access: Access = Access.ALREADY,
    ) {
        write(id, JSONObject().apply {
            put(URI, archiveUri); put(NAME, archiveName); put(BACKUP, backup.path); put(BACKUP_SIZE, backupSize)
            put(BACKUP_PRINT, backupFingerprint); put(NEW_SIZE, newSize); put(NEW_PRINT, newFingerprint); put(PHASE, REPLACING)
            put(RECORDS, JSONArray(recordIds)); put(ACCESS, access.name)
        })
    }

    fun restoreFailed(id: String) {
        setPhase(id, RESTORE_FAILED)
        refreshStuck()
    }

    /** The archive is whole: the journal and everything beside it go. */
    fun done(id: String) {
        folder(id).deleteRecursively()
        refreshStuck()
    }

    /** The user gave up the copy kept of an archive that could not be put back. */
    fun discard(id: String) = done(id)

    /** At start-up: interrupted replacements are undone, finished or orphan folders removed. Never throws. */
    fun recover(io: UpdateIo): List<RecoveryOutcome> {
        val outcomes = File(filesDir, ROOT).listFiles().orEmpty().mapNotNull { dir ->
            try {
                recoverOne(dir, io)
            } catch (_: Exception) {   // a full disk, a provider, a damaged file: the backup stays for the user
                stuckOf(dir, null)
            }
        }
        refreshStuck()
        _outcomes.value = outcomes.filterNot { it is RecoveryOutcome.Completed }
        return outcomes
    }

    fun shown() {
        _outcomes.value = emptyList()
    }

    private fun recoverOne(dir: File, io: UpdateIo): RecoveryOutcome? {
        val text = read(dir)
        if (text == null) {
            dir.deleteRecursively()   // no journal: the archive was never touched
            return null
        }
        val journal = runCatching { JSONObject(text) }.getOrNull() ?: return stuckOf(dir, null)
        val uri = journal.getString(URI)
        val name = journal.getString(NAME)
        val backup = File(journal.getString(BACKUP))
        if (journal.optString(PHASE) == RESTORE_FAILED) return RecoveryOutcome.RestoreFailed(dir.name, name, backup.path)
        val access = runCatching { Access.valueOf(journal.optString(ACCESS)) }.getOrDefault(Access.ALREADY)
        val now = io.facts(uri)
        val backupSize = journal.getLong(BACKUP_SIZE)
        val backupPrint = journal.getString(BACKUP_PRINT)
        if (now != null && now.size == journal.getLong(NEW_SIZE) && now.fingerprint == journal.getString(NEW_PRINT)) {
            finish(dir, uri, io, access)   // the new archive was written whole before the app closed
            val ids = journal.optJSONArray(RECORDS)?.let { a -> (0 until a.length()).map(a::getString) }.orEmpty()
            return RecoveryOutcome.Completed(uri, ids, now.size, now.fingerprint)
        }
        if (now != null && now.size == backupSize && now.fingerprint == backupPrint) {
            finish(dir, uri, io, access)   // never touched, or already put back: nothing to say
            return null
        }
        val whole = backup.isFile && backup.length() == backupSize && Replace.fingerprint(backup) == backupPrint
        val restored = whole && runCatching { Replace.copy(backup, uri, io) }.getOrDefault(false)
        if (!restored) {
            setPhase(dir.name, RESTORE_FAILED)
            return RecoveryOutcome.RestoreFailed(dir.name, name, backup.path)
        }
        finish(dir, uri, io, access)
        return RecoveryOutcome.Restored(name, uri)
    }

    private fun finish(dir: File, uri: String, io: UpdateIo, access: Access) {
        runCatching { io.releaseAccess(uri, access) }
        dir.deleteRecursively()
    }

    private fun stuckOf(dir: File, name: String?): RecoveryOutcome.RestoreFailed? {
        val backup = File(dir, BACKUP_FILE).takeIf { it.isFile } ?: return null
        return RecoveryOutcome.RestoreFailed(dir.name, name ?: backup.name, backup.path)
    }

    /** The journals waiting for the user, read from disk. */
    private fun refreshStuck() {
        _stuck.value = File(filesDir, ROOT).listFiles().orEmpty().mapNotNull { dir ->
            val journal = read(dir)?.let { runCatching { JSONObject(it) }.getOrNull() }
            when {
                journal == null -> if (read(dir) != null) stuckOf(dir, null) else null
                journal.optString(PHASE) == RESTORE_FAILED ->
                    RecoveryOutcome.RestoreFailed(dir.name, journal.optString(NAME), journal.optString(BACKUP))
                else -> null
            }
        }
    }

    /** The journal, or its temp file when the app died between writing it and renaming it. */
    private fun read(dir: File): String? =
        listOf(JOURNAL, JOURNAL + TEMP).map { File(dir, it) }.firstOrNull { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }

    private fun setPhase(id: String, phase: String) {
        val current = read(folder(id))?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
        runCatching { write(id, current.put(PHASE, phase)) }
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
        const val BACKUP_FILE = "backup.zip"
        const val URI = "uri"
        const val NAME = "name"
        const val BACKUP = "backup"
        const val BACKUP_SIZE = "backupSize"
        const val BACKUP_PRINT = "backupFingerprint"
        const val NEW_SIZE = "newSize"
        const val NEW_PRINT = "newFingerprint"
        const val RECORDS = "records"
        const val ACCESS = "access"
        const val PHASE = "phase"
        const val REPLACING = "replacing"
        const val RESTORE_FAILED = "restore-failed"
    }
}
