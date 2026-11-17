package io.github.martindavinci.lunaris.archives.edits

import io.github.martindavinci.lunaris.archives.di.CacheDir
import io.github.martindavinci.lunaris.archives.extract.FreeSpace
import io.github.martindavinci.lunaris.archives.safety.Limits
import io.github.martindavinci.lunaris.archives.session.FreeBytes
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Puts changed files back into a ZIP without ever leaving it broken: the original is first copied to a backup in
 * filesDir, the new archive is built and checked in cache/update, and only then written over the original, under
 * a journal. If that write fails the backup goes back; if the app dies, [UpdateJournal.recover] does it.
 */
class ZipUpdater @Inject constructor(
    private val io: UpdateIo,
    private val journal: UpdateJournal,
    @param:CacheDir private val cacheDir: File,
    private val freeBytes: FreeBytes,
) {
    /** [password] is wiped before this returns. */
    suspend fun update(archiveUri: String, records: List<EditRecord>, password: CharArray?): UpdateResult = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val work = File(File(cacheDir, WORK_ROOT), id)
        try {
            prepareAndReplace(id, work, archiveUri, records, password)
        } catch (e: CancellationException) {
            journal.done(id)
            throw e
        } catch (e: IOException) {
            journal.done(id)
            if (FreeSpace.isNoSpace(e)) UpdateResult.NoSpace else UpdateResult.Failed
        } catch (_: RuntimeException) {   // a provider's own exception: nothing was replaced yet
            journal.done(id)
            UpdateResult.Failed
        } finally {
            work.deleteRecursively()
            password?.fill(WIPED)
        }
    }

    private suspend fun prepareAndReplace(id: String, work: File, uri: String, records: List<EditRecord>, password: CharArray?): UpdateResult {
        val first = records.firstOrNull() ?: return UpdateResult.Failed
        if (records.any { it.pending == null }) return UpdateResult.Failed
        if (!io.canWrite(uri)) return UpdateResult.NotWritable
        val facts = io.facts(uri) ?: return UpdateResult.Failed
        if (!sameArchive(facts, first)) return UpdateResult.ArchiveChanged
        val edits = records.sumOf { checkNotNull(it.pending).size }
        // The backup, the work copy and zip4j's own copy while it removes entries, and the snapshots added twice.
        if (freeBytes.get() < facts.size * COPIES + edits * 2 + Limits.CACHE_MARGIN_BYTES) return UpdateResult.NoSpace

        val backup = File(journal.folder(id).apply { mkdirs() }, BACKUP)
        io.openInput(uri).use { input -> FileOutputStream(backup).use { out -> input.copyTo(out, Limits.WIRE_CHUNK_BYTES); out.fd.sync() } }
        val backupPrint = Replace.fingerprint(backup)
        if (backup.length() != first.archiveSize || backupPrint != first.archiveFingerprint) {
            journal.done(id)
            return UpdateResult.ArchiveChanged
        }
        val zip = File(work.apply { mkdirs() }, WORK)
        backup.copyTo(zip)
        currentCoroutineContext().ensureActive()
        when (ZipRewrite.rewrite(zip, records, password)) {
            ZipRewrite.Result.OK -> Unit
            ZipRewrite.Result.NEEDS_PASSWORD -> return UpdateResult.NeedsPassword.also { journal.done(id) }
            ZipRewrite.Result.WRONG_PASSWORD -> return UpdateResult.WrongPassword.also { journal.done(id) }
            ZipRewrite.Result.UNSUPPORTED -> return UpdateResult.Unsupported.also { journal.done(id) }
            ZipRewrite.Result.NO_SPACE -> return UpdateResult.NoSpace.also { journal.done(id) }
            ZipRewrite.Result.FAILED -> return UpdateResult.Failed.also { journal.done(id) }
        }
        currentCoroutineContext().ensureActive()
        val newPrint = Replace.fingerprint(zip)
        // From here on the original is touched: nothing may stop halfway, not even a cancel.
        return withContext(NonCancellable) { replace(id, uri, first.archiveName, backup, backupPrint, zip, newPrint) }
    }

    private fun replace(id: String, uri: String, name: String, backup: File, backupPrint: String, zip: File, newPrint: String): UpdateResult {
        val kept = runCatching { io.keepAccess(uri) }.getOrDefault(false)
        try {
            journal.begin(id, uri, name, backup, backup.length(), backupPrint, zip.length(), newPrint)
            if (attempt { Replace.copy(zip, uri, io) }) {
                journal.done(id)
                return UpdateResult.Done(zip.length(), runCatching { io.facts(uri)?.mtime }.getOrNull(), newPrint)
            }
            if (attempt { Replace.copy(backup, uri, io) }) {
                journal.done(id)
                return UpdateResult.Failed
            }
            runCatching { journal.restoreFailed(id) }   // the journal stays "replacing" otherwise: recovery tries again
            return UpdateResult.RestoreFailed(backup.path)
        } finally {
            if (kept) runCatching { io.releaseAccess(uri) }
        }
    }

    private inline fun attempt(action: () -> Boolean): Boolean = try {
        action()
    } catch (_: IOException) {
        false
    } catch (_: RuntimeException) {
        false
    }

    private fun sameArchive(facts: ArchiveFacts, record: EditRecord): Boolean =
        facts.size == record.archiveSize && facts.fingerprint == record.archiveFingerprint &&
            (facts.mtime == null || record.archiveMtime == null || facts.mtime == record.archiveMtime)

    private companion object {
        const val WORK_ROOT = "update"
        const val WORK = "work.zip"
        const val BACKUP = "backup.zip"
        const val COPIES = 3
        val WIPED = Char(0)
    }
}
