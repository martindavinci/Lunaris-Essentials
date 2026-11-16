package io.github.martindavinci.lunaris.archives.feature

/** Navigation routes; the browser takes the id of an open request (ArchiveSession.register). */
object Routes {
    const val ARG_ARCHIVE_ID = "archiveId"
    const val BROWSER = "browser/{$ARG_ARCHIVE_ID}"

    fun browser(id: String) = "browser/$id"
}
