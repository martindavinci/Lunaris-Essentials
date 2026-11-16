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
import io.github.martindavinci.lunaris.archives.files.EntryCopier
import io.github.martindavinci.lunaris.archives.files.EntryOpener
import io.github.martindavinci.lunaris.archives.files.FileUris
import io.github.martindavinci.lunaris.archives.files.PlatformMime

@Module
@InstallIn(SingletonComponent::class)
abstract class FilesModule {
    @Binds
    abstract fun bindOpener(copier: EntryCopier): EntryOpener

    companion object {
        private const val AUTHORITY_SUFFIX = ".fileprovider"

        @Provides
        fun provideFileUris(@ApplicationContext context: Context): FileUris = FileUris { file ->
            FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file).toString()
        }

        @Provides
        fun providePlatformMime(): PlatformMime = PlatformMime { ext -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) }
    }
}
