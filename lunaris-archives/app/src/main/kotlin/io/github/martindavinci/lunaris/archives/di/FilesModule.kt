package io.github.martindavinci.lunaris.archives.di

import android.content.Context
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.martindavinci.lunaris.archives.create.ContentCreateIo
import io.github.martindavinci.lunaris.archives.create.CreateIo
import io.github.martindavinci.lunaris.archives.create.DocumentsSourceTree
import io.github.martindavinci.lunaris.archives.create.SourceTree
import io.github.martindavinci.lunaris.archives.extract.ExtractAgain
import io.github.martindavinci.lunaris.archives.extract.ExtractMarker
import io.github.martindavinci.lunaris.archives.extract.ExtractStarter
import io.github.martindavinci.lunaris.archives.extract.KeyValue
import io.github.martindavinci.lunaris.archives.extract.PrefsExtractMarker
import io.github.martindavinci.lunaris.archives.files.EntryCopier
import io.github.martindavinci.lunaris.archives.files.EntryOpener
import io.github.martindavinci.lunaris.archives.files.FileUris
import io.github.martindavinci.lunaris.archives.files.PlatformMime
import io.github.martindavinci.lunaris.archives.files.ProtectedCopies
import io.github.martindavinci.lunaris.archives.files.UriRevoker
import io.github.martindavinci.lunaris.archives.edits.ContentSaveIo
import io.github.martindavinci.lunaris.archives.edits.ContentUpdateIo
import io.github.martindavinci.lunaris.archives.edits.PasswordSource
import io.github.martindavinci.lunaris.archives.edits.SaveIo
import io.github.martindavinci.lunaris.archives.session.ArchiveSession
import io.github.martindavinci.lunaris.archives.edits.EditLookup
import io.github.martindavinci.lunaris.archives.edits.UpdateIo
import io.github.martindavinci.lunaris.archives.edits.EditRecorder
import io.github.martindavinci.lunaris.archives.edits.EditWatcher
import io.github.martindavinci.lunaris.archives.files.CopyRecorder
import io.github.martindavinci.lunaris.archives.files.EditedCopies

@Module
@InstallIn(SingletonComponent::class)
abstract class FilesModule {
    @Binds
    abstract fun bindOpener(copier: EntryCopier): EntryOpener

    @Binds
    abstract fun bindMarker(marker: PrefsExtractMarker): ExtractMarker

    @Binds
    abstract fun bindAgain(starter: ExtractStarter): ExtractAgain

    @Binds
    abstract fun bindCreateIo(io: ContentCreateIo): CreateIo

    @Binds
    abstract fun bindSourceTree(tree: DocumentsSourceTree): SourceTree

    @Binds
    abstract fun bindProtectedCopies(watcher: EditWatcher): ProtectedCopies

    @Binds
    abstract fun bindEditedCopies(lookup: EditLookup): EditedCopies

    @Binds
    abstract fun bindCopyRecorder(recorder: EditRecorder): CopyRecorder

    @Binds
    abstract fun bindUpdateIo(io: ContentUpdateIo): UpdateIo

    @Binds
    abstract fun bindSaveIo(io: ContentSaveIo): SaveIo

    companion object {
        private const val AUTHORITY_SUFFIX = ".fileprovider"

        @Provides
        fun provideFileUris(@ApplicationContext context: Context): FileUris = FileUris { file ->
            FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file).toString()
        }

        /** A copy given to another app stops being reachable through its content URI. */
        @Provides
        fun provideUriRevoker(@ApplicationContext context: Context, uris: FileUris): UriRevoker = UriRevoker { file ->
            runCatching {
                context.revokeUriPermission(
                    android.net.Uri.parse(uris.uriFor(file)),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }

        /** Passwords of open or remembered archives, for updating their entries. */
        @Provides
        fun providePasswordSource(session: ArchiveSession): PasswordSource = PasswordSource(session::passwordForKey)

        private const val MARKER_FILE = "extract_marker"
        private const val MARKER_KEY = "token"

        /** Written with apply(): the marker is a clean-up hint, not worth a disk sync per file. */
        @Provides
        fun provideMarkerStore(@ApplicationContext context: Context): KeyValue {
            val prefs = context.getSharedPreferences(MARKER_FILE, Context.MODE_PRIVATE)
            return object : KeyValue {
                override fun get(): String? = prefs.getString(MARKER_KEY, null)
                override fun put(value: String) = prefs.edit().putString(MARKER_KEY, value).apply()
                override fun remove() = prefs.edit().remove(MARKER_KEY).apply()
            }
        }

        @Provides
        fun providePlatformMime(): PlatformMime = PlatformMime { ext -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) }
    }
}
