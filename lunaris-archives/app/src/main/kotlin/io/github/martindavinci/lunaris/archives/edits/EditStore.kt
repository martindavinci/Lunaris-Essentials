package io.github.martindavinci.lunaris.archives.edits

import io.github.martindavinci.lunaris.archives.di.FilesDir
import io.github.martindavinci.lunaris.archives.safety.Limits
import java.io.File
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
    val changes: StateFlow<List<EditRecord>> = _changes.asStateFlow()

    @Synchronized
    fun all(): List<EditRecord> = _changes.value

    /** Adds [record], or keeps the one already there for the same copy (its baseline and change stay). */
    @Synchronized
    fun put(record: EditRecord) {
        val current = _changes.value
        if (current.any { it.copyPath == record.copyPath }) return
        write(trimmed(current + record))
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

    private fun write(list: List<EditRecord>) {
        val target = file
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, FILE + TEMP_SUFFIX)
        temp.writeText(EditCodec.encode(list))
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
        _changes.value = list
    }

    private companion object {
        const val FOLDER = "edits"
        const val FILE = "records.json"
        const val TEMP_SUFFIX = ".new"
    }
}
