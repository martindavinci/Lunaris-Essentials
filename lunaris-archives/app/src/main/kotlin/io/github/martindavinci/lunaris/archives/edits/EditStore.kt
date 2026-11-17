package io.github.martindavinci.lunaris.archives.edits

import io.github.martindavinci.lunaris.archives.di.FilesDir
import io.github.martindavinci.lunaris.archives.safety.Limits
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The register of copies handed out for editing, in filesDir/edits/records.json, written whole and atomically. */
@Singleton
class EditStore @Inject constructor(@param:FilesDir private val filesDir: File) {
    private val file get() = File(File(filesDir, FOLDER), FILE)
    private val _changes = MutableStateFlow(read())
    /** False after a write the disk refused: the next one is not skipped even if nothing changed. */
    private var saved = true
    val changes: StateFlow<List<EditRecord>> = _changes.asStateFlow()

    /** No lock: a snapshot of the register, never blocked by a write in progress. */
    fun all(): List<EditRecord> = _changes.value

    /**
     * Adds [record], or keeps the one already there for the same copy (its baseline and change stay).
     * Returns the records dropped to stay under [Limits.MAX_EDIT_RECORDS]: their copies are no longer followed.
     */
    @Synchronized
    fun put(record: EditRecord): List<EditRecord> {
        val current = _changes.value
        if (current.any { it.copyPath == record.copyPath }) return emptyList()
        val kept = trimmed(current + record)
        write(kept)
        return (current + record).filter { it !in kept }
    }

    @Synchronized
    fun remove(ids: Collection<String>) {
        write(_changes.value.filterNot { it.id in ids })
    }

    /** Each record through [transform]; null removes it. */
    @Synchronized
    fun update(transform: (EditRecord) -> EditRecord?) {
        write(_changes.value.mapNotNull(transform))
    }

    private fun trimmed(list: List<EditRecord>): List<EditRecord> {
        if (list.size <= Limits.MAX_EDIT_RECORDS) return list
        val drop = list.filter { it.pending == null }.sortedBy { it.createdMillis }
            .take(list.size - Limits.MAX_EDIT_RECORDS).map { it.id }.toSet()
        return list.filterNot { it.id in drop }
    }

    private fun read(): List<EditRecord> = runCatching { EditCodec.decode(file.readText()) }.getOrDefault(emptyList())

    /** A full disk keeps the register in memory for this run: an edit is never lost for a failed write. */
    private fun write(list: List<EditRecord>) {
        if (list == _changes.value && saved) return
        _changes.value = list
        saved = false
        try {
            val target = file
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, FILE + TEMP_SUFFIX)
            FileOutputStream(temp).use { out ->
                out.write(EditCodec.encode(list).toByteArray())
                out.fd.sync()
            }
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
            saved = true
        } catch (_: IOException) {
            Unit
        }
    }

    private companion object {
        const val FOLDER = "edits"
        const val FILE = "records.json"
        const val TEMP_SUFFIX = ".new"
    }
}
