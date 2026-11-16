package io.github.martindavinci.lunaris.archives.safety

/**
 * Spec "Rapporto di espansione": after [checkAfterBytes] of one entry, ask when output/input > [maxRatio].
 *
 * The engine reports its position in the archive, not how much it has read (libarchive's counter
 * follows seeks). Every engine reports its position right after DATA_START, so the first report
 * after [onEntryStart] is the start of the entry.
 */
class RatioGuard(
    private val checkAfterBytes: Long = Limits.RATIO_CHECK_AFTER_BYTES,
    private val maxRatio: Long = Limits.MAX_RATIO,
) {
    enum class Verdict { CONTINUE, ASK }

    private var entryStart = UNKNOWN
    private var position = 0L
    private var entryOutput = 0L
    private var allowed = false

    fun onEntryStart() {
        entryStart = UNKNOWN
        entryOutput = 0
        allowed = false
    }

    /** From PROGRESS messages: the engine's position in the archive. */
    fun onArchiveBytes(total: Long) {
        if (entryStart == UNKNOWN) entryStart = total
        position = total
    }

    fun onOutput(bytes: Long): Verdict {
        entryOutput += bytes
        if (allowed || entryOutput < checkAfterBytes) return Verdict.CONTINUE
        // No report yet, or a position that went backwards: assume the least input, and ask.
        val input = if (entryStart == UNKNOWN) 1 else (position - entryStart).coerceAtLeast(1)
        return if (entryOutput / input > maxRatio) Verdict.ASK else Verdict.CONTINUE
    }

    /** The user said Continue. */
    fun allowCurrentEntry() { allowed = true }

    private companion object { const val UNKNOWN = -1L }
}
