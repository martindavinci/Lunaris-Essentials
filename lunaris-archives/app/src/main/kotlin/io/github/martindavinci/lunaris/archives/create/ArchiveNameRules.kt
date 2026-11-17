package io.github.martindavinci.lunaris.archives.create

import io.github.martindavinci.lunaris.archives.safety.Limits
import java.util.Locale

/** The name typed for a new ZIP: what is allowed, what is suggested, and the file name it becomes. */
object ArchiveNameRules {
    /** Sentinel for "several shared files": the ViewModel shows the translated text instead. */
    const val SHARED_FILES_FALLBACK = "Shared files"
    private const val FORBIDDEN = "/\\:*?\"<>|"
    private const val TAR_SUFFIX = ".tar"
    private const val LAST_CONTROL = 0x1F
    private const val DELETE = 0x7F

    fun isValid(name: String, format: ArchiveFormat = ArchiveFormat.ZIP): Boolean {
        if (name.isBlank()) return false
        if (name.any { it in FORBIDDEN || it.code <= LAST_CONTROL || it.code == DELETE }) return false
        return fileName(name, format).toByteArray(Charsets.UTF_8).size <= Limits.MAX_COMPONENT_BYTES
    }

    fun suggested(names: List<String>, folder: String?): String = when {
        folder != null -> folder
        names.size == 1 -> names.single().let { name ->
            val dot = name.lastIndexOf('.')
            if (dot > 0) name.substring(0, dot) else name
        }
        else -> SHARED_FILES_FALLBACK
    }

    /** [base] with the suffix of [format], unless it is typed already; a bare ".tar" is completed for a packed TAR. */
    fun fileName(base: String, format: ArchiveFormat = ArchiveFormat.ZIP): String {
        val lower = base.lowercase(Locale.ROOT)
        return when {
            lower.endsWith(format.suffix) -> base
            format.isTar && format != ArchiveFormat.TAR && lower.endsWith(TAR_SUFFIX) -> base + format.suffix.removePrefix(TAR_SUFFIX)
            else -> base + format.suffix
        }
    }
}
