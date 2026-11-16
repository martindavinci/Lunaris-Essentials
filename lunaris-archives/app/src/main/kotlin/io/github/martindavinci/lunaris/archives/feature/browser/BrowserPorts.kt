package io.github.martindavinci.lunaris.archives.feature.browser

import android.net.Uri
import io.github.martindavinci.lunaris.archives.browse.SortOrder
import io.github.martindavinci.lunaris.archives.session.ArchiveSource
import io.github.martindavinci.lunaris.archives.session.ArchiveSources
import io.github.martindavinci.lunaris.archives.session.OpenArchive
import io.github.martindavinci.lunaris.archives.session.SourceResult
import io.github.martindavinci.lunaris.archives.settings.AppSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Where archives come from; a small door so the view model is tested without Android. */
interface SourcesPort {
    suspend fun open(uri: String): SourceResult
    suspend fun copyIn(uri: String, bytes: Long?, onProgress: (Long) -> Unit): ArchiveSource
}

/** The recents, told where the user is in an archive. */
interface RecentsPort {
    suspend fun visited(archive: OpenArchive, path: List<String>)
}

/** The settings the browser reads, and the sort it saves. */
interface BrowserPreferences {
    val settings: Flow<AppSettings>
    suspend fun saveSort(order: SortOrder)
}

class SourcesAdapter @Inject constructor(private val sources: ArchiveSources) : SourcesPort {
    override suspend fun open(uri: String): SourceResult = sources.open(Uri.parse(uri))
    override suspend fun copyIn(uri: String, bytes: Long?, onProgress: (Long) -> Unit): ArchiveSource =
        sources.copyIn(Uri.parse(uri), bytes, onProgress)
}
