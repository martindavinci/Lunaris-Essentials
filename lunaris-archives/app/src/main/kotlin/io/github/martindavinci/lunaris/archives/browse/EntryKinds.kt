package io.github.martindavinci.lunaris.archives.browse

import io.github.martindavinci.lunaris.archives.archive.EntryType
import java.util.Locale

/** What a row shows as its icon. */
enum class EntryKind { FOLDER, TEXT, TABLE, PDF, IMAGE, VIDEO, AUDIO, ARCHIVE, OTHER }

object EntryKinds {
    private val BY_EXTENSION: Map<String, EntryKind> = buildMap {
        listOf("txt", "md", "markdown", "log", "json", "xml", "yaml", "yml", "toml", "ini", "conf", "cfg", "kt", "kts",
            "java", "c", "h", "cpp", "py", "js", "ts", "html", "css", "sh", "gradle", "properties", "env", "rtf")
            .forEach { put(it, EntryKind.TEXT) }
        listOf("csv", "tsv", "xls", "xlsx", "ods").forEach { put(it, EntryKind.TABLE) }
        put("pdf", EntryKind.PDF)
        listOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "svg").forEach { put(it, EntryKind.IMAGE) }
        listOf("mp4", "mkv", "webm", "mov", "avi", "3gp").forEach { put(it, EntryKind.VIDEO) }
        listOf("mp3", "flac", "ogg", "opus", "m4a", "wav", "aac").forEach { put(it, EntryKind.AUDIO) }
        listOf("zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "tbz2", "xz", "txz", "zst", "tzst", "lz4", "iso", "cpio")
            .forEach { put(it, EntryKind.ARCHIVE) }
    }

    fun of(name: String, type: EntryType): EntryKind {
        if (type == EntryType.DIRECTORY) return EntryKind.FOLDER
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return BY_EXTENSION[ext] ?: EntryKind.OTHER
    }
}
