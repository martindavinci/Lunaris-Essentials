package io.github.martindavinci.lunaris.archives.edits

import io.github.martindavinci.lunaris.archives.di.Background
import io.github.martindavinci.lunaris.archives.di.FilesDir
import io.github.martindavinci.lunaris.archives.files.ProtectedCopies
import io.github.martindavinci.lunaris.archives.files.UriRevoker
import io.github.martindavinci.lunaris.archives.safety.Limits
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Notices copies changed in other apps. A changed copy stays where it is (an editor may save again) and a snapshot
 * of it goes to filesDir/edits/pending: the latest bytes always win, and clearing the cache cannot lose them.
 * Every look at the copies, and every update built on them, holds [lock].
 */
@Singleton
class EditWatcher @Inject constructor(
    private val store: EditStore,
    @param:FilesDir private val filesDir: File,
    private val revoker: UriRevoker,
    @param:Background dispatcher: CoroutineDispatcher,
) : ProtectedCopies {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var soon: Job? = null
    val lock = Mutex()
    val changed: StateFlow<List<ChangedArchive>> =
        store.changes.map(ChangedArchive::of).stateIn(scope, SharingStarted.Eagerly, ChangedArchive.of(store.all()))

    override fun files(): Set<File> = store.all().mapTo(HashSet()) { File(it.copyPath) }

    suspend fun scan(now: Long = System.currentTimeMillis()) = lock.withLock { scanLocked(now) }

    /** For a caller that already holds [lock]. */
    fun scanLocked(now: Long = System.currentTimeMillis()) {
        store.update { look(it, now) }
        dropOrphans()
    }

    /** A scan in the background, at most one at a time (each return to the app asks for one). */
    @Synchronized
    fun scanSoon() {
        if (soon?.isActive == true) return
        soon = scope.launch { scan() }
    }

    /** One record looked at again; null when it is gone. */
    suspend fun check(record: EditRecord, now: Long = System.currentTimeMillis()): EditRecord? = lock.withLock {
        var result: EditRecord? = null
        store.update { if (it.id == record.id) look(it, now).also { looked -> result = looked } else it }
        result
    }

    private fun look(record: EditRecord, now: Long): EditRecord? {
        val live = File(record.copyPath)
        if (!live.isFile) return record.takeIf { it.pending != null }
        val base = record.pending?.let { Triple(it.size, it.mtime, it.sha256) } ?: Triple(record.size, record.mtime, record.sha256)
        if (live.length() == base.first && live.lastModified() == base.second) {
            val stale = record.pending == null && now - record.createdMillis > Limits.TEMP_MAX_AGE_MILLIS
            if (stale) revoker.revoke(live)
            return record.takeUnless { stale }
        }
        return try {
            snapshot(record, live)
        } catch (_: IOException) {
            record   // the copy is being written right now: next time
        }
    }

    /** Copies the live file first, then hashes the copy: a write in between cannot make the two disagree. */
    private fun snapshot(record: EditRecord, live: File): EditRecord {
        val folder = File(pendingRoot, record.id).apply { mkdirs() }
        val mtime = live.lastModified()
        val temp = File(folder, TEMP)
        live.copyTo(temp, overwrite = true)
        val sha = Fingerprint.sha256(temp)
        val baseSha = record.pending?.sha256 ?: record.sha256
        if (sha == baseSha) {
            temp.delete()
            // The same bytes, so the same size: only the date moved.
            return record.pending?.let { record.copy(pending = it.copy(mtime = mtime)) } ?: record.copy(mtime = mtime)
        }
        val target = File(folder, live.name)
        if (!temp.renameTo(target)) {
            target.delete()
            if (!temp.renameTo(target)) throw IOException("snapshot not moved")
        }
        return record.copy(pending = Snapshot(target.path, target.length(), mtime, sha))
    }

    private fun dropOrphans() {
        val kept = store.all().mapNotNullTo(HashSet()) { it.pending?.let { p -> File(p.path).parentFile } }
        pendingRoot.listFiles()?.forEach { if (it !in kept) it.deleteRecursively() }
    }

    private val pendingRoot get() = File(filesDir, PENDING)

    private companion object {
        const val PENDING = "edits/pending"
        const val TEMP = ".snapshot"
    }
}
