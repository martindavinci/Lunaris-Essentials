package io.github.martindavinci.lunaris.archives.safety

import io.github.martindavinci.lunaris.archives.archive.EntryType

/** Spec "Percorsi: regole esatte", rules 1–8. Writing rules 9–10 are in [SafeComponent]. */
object SafePath {
    sealed interface Result {
        data class Ok(val components: List<String>) : Result
        data class Skipped(val reason: SkipReason) : Result
    }

    private val DRIVE_LETTER = Regex("^[A-Za-z]:")
    private const val PARENT = ".."
    private const val CURRENT = "."

    fun check(name: String): Result {
        if (name.indexOf('\u0000') >= 0) return Result.Skipped(SkipReason.INVALID_NAME)
        val slashed = name.replace('\\', '/')
        if (slashed.startsWith("/") || DRIVE_LETTER.containsMatchIn(slashed)) {
            return Result.Skipped(SkipReason.OUTSIDE_FOLDER)
        }
        val parts = slashed.split('/').filter { it.isNotEmpty() && it != CURRENT }
        if (parts.any { it == PARENT }) return Result.Skipped(SkipReason.OUTSIDE_FOLDER)
        val tooBig = parts.size > Limits.MAX_PATH_DEPTH || slashed.toByteArray(Charsets.UTF_8).size > Limits.MAX_PATH_BYTES
        if (parts.isEmpty() || tooBig) return Result.Skipped(SkipReason.INVALID_NAME)
        return Result.Ok(parts)
    }

    fun forType(type: EntryType): SkipReason? = when (type) {
        EntryType.FILE, EntryType.DIRECTORY -> null
        EntryType.SYMLINK, EntryType.HARDLINK -> SkipReason.LINK
        EntryType.CHAR_DEVICE, EntryType.BLOCK_DEVICE, EntryType.FIFO, EntryType.SOCKET -> SkipReason.DEVICE_FILE
    }

    fun evaluate(name: String, type: EntryType): Result = forType(type)?.let { Result.Skipped(it) } ?: check(name)
}
