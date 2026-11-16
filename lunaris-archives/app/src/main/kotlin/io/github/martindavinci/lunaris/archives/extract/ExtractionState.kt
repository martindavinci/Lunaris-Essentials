package io.github.martindavinci.lunaris.archives.extract

import io.github.martindavinci.lunaris.archives.archive.ArchiveEntry
import io.github.martindavinci.lunaris.archives.session.OpenArchive

sealed interface ExtractionState {
    data object Idle : ExtractionState
    data class Conflicts(val pending: List<Conflict>, val current: Int) : ExtractionState
    data class Running(val doneFiles: Int, val totalFiles: Int, val doneBytes: Long, val totalBytes: Long, val current: String) : ExtractionState
    data class AskLarge(val name: String) : ExtractionState
    data class Finished(
        val written: Int, val total: Int, val skipped: List<SkippedEntry>, val damaged: List<String>,
        val location: String?, val stoppedEarly: Boolean,
    ) : ExtractionState
    data class Failed(val reason: ExtractFailure, val neededBytes: Long? = null, val freeBytes: Long? = null) : ExtractionState
}

enum class ExtractFailure { NO_SPACE, RAN_OUT_OF_SPACE, FOLDER_GONE, READER_STOPPED, WRONG_PASSWORD }

/**
 * What to extract, where. [password] is a copy the runner wipes when it is done.
 * [onWrongPassword]: the run ended because the password was wrong, so it should not be offered again.
 */
class ExtractJob(
    val archive: OpenArchive, val entries: List<ArchiveEntry>, val password: CharArray?, val target: SafTarget,
    val onWrongPassword: () -> Unit = {},
)

/** Remembers the file being written, so a restart after process death can delete it. */
interface ExtractMarker {
    fun set(token: String)
    fun clear()
}
