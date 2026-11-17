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

/** Keeping access to an archive across restarts: read, and write when the picker gave it (updates need it). */
interface UriPermissions {
    /** False when the provider does not let it last: then the archive is not remembered. */
    fun takeRead(uri: String): Boolean
    fun release(uri: String)
}

/** The two ContentResolver calls, so that the choice of flags can be tested on the JVM. */
interface PermissionCalls {
    fun take(uri: String, flags: Int): Boolean
    fun release(uri: String, flags: Int)
}

class ResolverPermissionCalls @Inject constructor(private val resolver: ContentResolver) : PermissionCalls {
    override fun take(uri: String, flags: Int): Boolean = runCatching { resolver.takePersistableUriPermission(Uri.parse(uri), flags) }.isSuccess

    override fun release(uri: String, flags: Int) {
        runCatching { resolver.releasePersistableUriPermission(Uri.parse(uri), flags) }
    }
}

class ContentUriPermissions @Inject constructor(private val calls: PermissionCalls) : UriPermissions {
    override fun takeRead(uri: String): Boolean = calls.take(uri, READ or WRITE) || calls.take(uri, READ)

    override fun release(uri: String) {
        calls.release(uri, READ or WRITE)
        calls.release(uri, READ)
    }

    private companion object {
        const val READ = Intent.FLAG_GRANT_READ_URI_PERMISSION
        const val WRITE = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}

/** An archive updated under another URI (picked again to be written): the recents follow it. */
class RecentsMover @Inject constructor(private val store: RecentsStore, private val permissions: UriPermissions) :
    io.github.martindavinci.lunaris.archives.edits.RecentsMove {
    override suspend fun moved(oldUri: String, newUri: String, sizeBytes: Long) {
        if (!store.move(oldUri, newUri, sizeBytes)) return
        if (!permissions.takeRead(newUri)) store.remove(newUri)
        permissions.release(oldUri)
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
