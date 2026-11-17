package io.github.martindavinci.lunaris.archives.edits

import io.github.martindavinci.lunaris.archives.files.UriRevoker
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface EditOutcome {
    data object Updated : EditOutcome
    data class Refused(val result: UpdateResult) : EditOutcome
    data object Saved : EditOutcome
    data object Ignored : EditOutcome
    data object Failed : EditOutcome
}

/** Writing a changed file somewhere the user picked (ContentResolver on the device). */
interface SaveIo {
    fun write(target: String, from: File)

    /** A new document in [tree]; the provider gives it another name when [name] is taken. */
    fun createIn(tree: String, name: String, mime: String): String
}

/** A password kept for the archive with this session key, if any. */
fun interface PasswordSource {
    fun find(archiveKey: String): CharArray?
}

/** The recents follow an archive written under another URI, with its new size. */
fun interface RecentsMove {
    suspend fun moved(oldUri: String, newUri: String, sizeBytes: Long)
}

/**
 * What the user decides about the changed files of one archive. Each decision first looks at the copies again
 * (the latest bytes win) and holds the watcher's lock until its records are settled.
 */
@Singleton
class EditActions @Inject constructor(
    private val store: EditStore,
    private val watcher: EditWatcher,
    private val updater: ZipUpdater,
    private val io: UpdateIo,
    private val revoker: UriRevoker,
    private val passwords: PasswordSource,
    private val saver: SaveIo,
    private val recents: RecentsMove,
) {
    /** [password] (wiped) or a remembered one; [writableUri] is the same archive picked again with the right to write. */
    suspend fun update(archiveUri: String, password: CharArray?, writableUri: String? = null): EditOutcome = watcher.lock.withLock {
        withContext(Dispatchers.IO) { watcher.scanLocked() }
        val records = pending(archiveUri)
        if (records.isEmpty()) return@withLock EditOutcome.Failed.also { password?.fill(WIPED) }
        val target = writableUri ?: archiveUri
        val secret = password ?: passwords.find(records.first().archiveKey)
        when (val result = updater.update(target, records, secret)) {
            is UpdateResult.Done -> {
                withContext(Dispatchers.IO) { settle(archiveUri, target, records, result) }
                if (target != archiveUri) recents.moved(archiveUri, target, result.newSize)
                EditOutcome.Updated
            }
            UpdateResult.Failed, is UpdateResult.RestoreFailed -> {
                withContext(Dispatchers.IO) { refresh(archiveUri) }
                EditOutcome.Refused(result)
            }
            else -> EditOutcome.Refused(result)
        }
    }

    suspend fun saveOne(archiveUri: String, targetUri: String): EditOutcome = closing(archiveUri) { records ->
        saver.write(targetUri, File(checkNotNull(records.single().pending).path))
    }

    suspend fun saveAll(archiveUri: String, treeUri: String): EditOutcome = closing(archiveUri) { records ->
        records.forEach { r ->
            val snapshot = File(checkNotNull(r.pending).path)
            saver.write(saver.createIn(treeUri, snapshot.name, MIME_ANY), snapshot)
        }
    }

    suspend fun ignore(archiveUri: String): EditOutcome =
        closing(archiveUri, deleteCopies = true) {}.let { if (it == EditOutcome.Saved) EditOutcome.Ignored else it }

    /** The picked document is this archive: same size and same fingerprint. */
    suspend fun matchesArchive(archiveUri: String, pickedUri: String): Boolean = withContext(Dispatchers.IO) {
        val record = store.all().firstOrNull { it.archiveUri == archiveUri } ?: return@withContext false
        val facts = io.facts(pickedUri) ?: return@withContext false
        facts.size == record.archiveSize && facts.fingerprint == record.archiveFingerprint
    }

    private suspend fun closing(archiveUri: String, deleteCopies: Boolean = false, action: (List<EditRecord>) -> Unit): EditOutcome =
        watcher.lock.withLock {
            withContext(Dispatchers.IO) {
                watcher.scanLocked()
                val records = pending(archiveUri)
                if (records.isEmpty()) return@withContext EditOutcome.Failed
                try {
                    action(records)
                } catch (_: IOException) {
                    return@withContext EditOutcome.Failed
                } catch (_: RuntimeException) {   // the provider of the target refused
                    return@withContext EditOutcome.Failed
                }
                close(records, deleteCopies)
                EditOutcome.Saved
            }
        }

    /**
     * After an update: the written records are closed, unless their copy changed again meanwhile (then the bytes
     * just written become their baseline and the change waits). Every record of the archive follows its new state.
     */
    private fun settle(archiveUri: String, target: String, written: List<EditRecord>, done: UpdateResult.Done) {
        val ids = written.associateBy { it.id }
        val closed = ArrayList<EditRecord>()
        store.update { r ->
            if (r.archiveUri != archiveUri) return@update r
            val moved = r.copy(archiveUri = target, archiveSize = done.newSize, archiveMtime = done.newMtime, archiveFingerprint = done.fingerprint)
            val was = ids[r.id] ?: return@update moved
            val snapshot = checkNotNull(was.pending)
            val live = File(r.copyPath)
            val again = live.isFile && Fingerprint.sha256(live) != snapshot.sha256
            if (again) moved.copy(size = snapshot.size, mtime = snapshot.mtime, sha256 = snapshot.sha256, pending = null)
            else null.also { closed += r }
        }
        written.forEach { r -> File(checkNotNull(r.pending).path).parentFile?.deleteRecursively() }
        closed.forEach { revoker.revoke(File(it.copyPath)) }
    }

    /** A failed update rewrote the original from its backup: its date changed, its bytes did not. */
    private fun refresh(archiveUri: String) {
        val facts = io.facts(archiveUri) ?: return
        store.update { r ->
            if (r.archiveUri == archiveUri && r.archiveSize == facts.size && r.archiveFingerprint == facts.fingerprint) r.copy(archiveMtime = facts.mtime) else r
        }
    }

    private fun close(records: List<EditRecord>, deleteCopies: Boolean) {
        store.remove(records.map { it.id })
        records.forEach { r ->
            File(checkNotNull(r.pending).path).parentFile?.deleteRecursively()
            val copy = File(r.copyPath)
            revoker.revoke(copy)
            if (deleteCopies) copy.delete()
        }
    }

    private fun pending(archiveUri: String) = store.all().filter { it.archiveUri == archiveUri && it.pending != null }

    private companion object {
        const val MIME_ANY = "application/octet-stream"
        val WIPED = Char(0)
    }
}
