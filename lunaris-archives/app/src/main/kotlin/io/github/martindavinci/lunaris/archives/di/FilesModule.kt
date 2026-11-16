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
import io.github.martindavinci.lunaris.archives.extract.ExtractMarker
import io.github.martindavinci.lunaris.archives.extract.KeyValue
import io.github.martindavinci.lunaris.archives.extract.PrefsExtractMarker
import io.github.martindavinci.lunaris.archives.files.EntryCopier
import io.github.martindavinci.lunaris.archives.files.EntryOpener
import io.github.martindavinci.lunaris.archives.files.FileUris
import io.github.martindavinci.lunaris.archives.files.PlatformMime

@Module
@InstallIn(SingletonComponent::class)
abstract class FilesModule {
    @Binds
    abstract fun bindOpener(copier: EntryCopier): EntryOpener

    @Binds
    abstract fun bindMarker(marker: PrefsExtractMarker): ExtractMarker

    companion object {
        private const val AUTHORITY_SUFFIX = ".fileprovider"

        @Provides
        fun provideFileUris(@ApplicationContext context: Context): FileUris = FileUris { file ->
            FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file).toString()
        }

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
