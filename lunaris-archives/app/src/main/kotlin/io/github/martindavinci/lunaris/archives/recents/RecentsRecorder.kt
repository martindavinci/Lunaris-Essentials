package io.github.martindavinci.lunaris.archives.recents

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.feature.browser.RecentsPort
import io.github.martindavinci.lunaris.archives.session.OpenArchive
import io.github.martindavinci.lunaris.archives.settings.AppSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Keeping a read permission across restarts (ContentResolver on the device). */
interface UriPermissions {
    /** False when the provider does not let it last: then the archive is not remembered. */
    fun takeRead(uri: String): Boolean
    fun release(uri: String)
}

class ContentUriPermissions @Inject constructor(private val resolver: ContentResolver) : UriPermissions {
    override fun takeRead(uri: String): Boolean =
        runCatching { resolver.takePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }.isSuccess

    override fun release(uri: String) {
        runCatching { resolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
}

/**
 * The user's rule: an archive is remembered only when the permission to read it lasts.
 * Nested archives and copies are never remembered; encrypted names never leave a path behind.
 */
class RecentsRecorder(
    private val store: RecentsStore,
    private val settings: Flow<AppSettings>,
    private val permissions: UriPermissions,
    private val clock: () -> Long,
) : RecentsPort {
    @Inject constructor(store: RecentsStore, settings: SettingsSource, permissions: UriPermissions) :
        this(store, settings.settings, permissions, System::currentTimeMillis)

    override suspend fun visited(archive: OpenArchive, path: List<String>) {
        if (archive.parentId != null || !archive.source.uri.startsWith(CONTENT)) return
        if (!settings.first().rememberRecents) return
        if (!permissions.takeRead(archive.source.uri)) return
        val index = archive.index
        val item = RecentArchive(
            uri = archive.source.uri, name = archive.source.name, formatLabel = index.formatLabel, sizeBytes = archive.source.size,
            fileCount = index.entries.count { it.type == EntryType.FILE }, locked = index.encryption != Encryption.NONE, lastOpenedMillis = clock(),
            lastPath = path.takeIf { index.encryption != Encryption.HEADERS },
        )
        store.remember(item).forEach { permissions.release(it.uri) }
    }

    private companion object { const val CONTENT = "content:" }
}

/** Where the settings come from (SettingsStore), so the recorder does not need the whole store. */
interface SettingsSource {
    val settings: Flow<AppSettings>
}
