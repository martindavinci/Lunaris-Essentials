package io.github.martindavinci.lunaris.archives.extract

import io.github.martindavinci.lunaris.archives.safety.Limits
import io.github.martindavinci.lunaris.archives.safety.SafeComponent
import java.util.Locale

/** "notes.md" → "notes (1).md", the first number not in [taken] (names lower-cased with Locale.ROOT by the caller). */
object KeepBothNamer {
    fun name(name: String, taken: Set<String>): String {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (true) {
            val suffix = " ($n)"
            val candidate = SafeComponent.forWriting(fit(base, suffix + ext) + suffix + ext) ?: (suffix.trim() + ext)
            if (candidate.lowercase(Locale.ROOT) !in taken) return candidate
            n++
        }
    }

    /** [base] cut by whole characters so that it and [tail] fit in one name. */
    private fun fit(base: String, tail: String): String {
        val room = Limits.MAX_COMPONENT_BYTES - tail.toByteArray(Charsets.UTF_8).size
        var cut = base
        while (cut.isNotEmpty() && cut.toByteArray(Charsets.UTF_8).size > room) {
            cut = cut.substring(0, cut.offsetByCodePoints(cut.length, -1))
        }
        return cut
    }
}
