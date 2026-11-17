package io.github.martindavinci.lunaris.archives.create

sealed interface CreateState {
    data object Idle : CreateState
    data class Running(val added: Int, val total: Int, val current: String, val doneBytes: Long, val totalBytes: Long) : CreateState
    data class Finished(val name: String, val sizeBytes: Long, val location: String?, val uri: String, val skipped: List<String>) : CreateState
    data class Failed(val reason: CreateFailure) : CreateState
}

enum class CreateFailure { NO_SPACE, SOURCE_GONE, TARGET_GONE, NO_MEMORY }

/** Reading and writing the documents of a creation (ContentResolver on the device). */
interface CreateIo {
    fun openInput(uri: String): java.io.InputStream
    fun openOutput(uri: String): java.io.OutputStream
    fun delete(uri: String)
    fun size(uri: String): Long?
    fun location(uri: String): String?
}
