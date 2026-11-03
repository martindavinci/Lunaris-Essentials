package io.github.martindavinci.lunaris.pdf.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.martindavinci.lunaris.pdf.annotate.EditedFileStore
import io.github.martindavinci.lunaris.pdf.annotate.SignatureStore
import io.github.martindavinci.lunaris.pdf.recents.ThumbnailStore
import java.io.File
import javax.inject.Singleton

/** A damaged file starts over empty: otherwise the app could not get past its first screen. */
private const val THUMBNAILS_FOLDER = "thumbnails"
private const val SIGNATURES_FOLDER = "signatures"
private const val EDITED_FOLDER = "edited"

private val Context.dataStore by preferencesDataStore(
    name = "pdf_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore

    /** In the folder Android never backs up. */
    @Provides
    @Singleton
    fun provideSignatures(@ApplicationContext context: Context): SignatureStore =
        SignatureStore(File(context.noBackupFilesDir, SIGNATURES_FOLDER))

    @Provides
    fun provideEditedFiles(@ApplicationContext context: Context): EditedFileStore =
        EditedFileStore(File(context.cacheDir, EDITED_FOLDER))

    @Provides
    @Singleton
    fun provideThumbnails(@ApplicationContext context: Context): ThumbnailStore =
        ThumbnailStore(File(context.noBackupFilesDir, THUMBNAILS_FOLDER))
}
