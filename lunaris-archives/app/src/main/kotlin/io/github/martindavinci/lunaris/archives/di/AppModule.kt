package io.github.martindavinci.lunaris.archives.di

import android.content.ContentResolver
import android.content.Context
import android.os.storage.StorageManager
import io.github.martindavinci.lunaris.archives.session.FreeBytes
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
import java.io.File
import java.io.IOException
import javax.inject.Qualifier
import javax.inject.Singleton

/** A damaged file starts over empty: otherwise the app could not get past its first screen. */
private val Context.dataStore by preferencesDataStore(
    name = "archives_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** The app's cache folder (the system may clear it). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CacheDir

/** The app's private files folder. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FilesDir

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @CacheDir
    fun provideCacheDir(@ApplicationContext context: Context): File = context.cacheDir

    @Provides
    @FilesDir
    fun provideFilesDir(@ApplicationContext context: Context): File = context.filesDir

    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver = context.contentResolver

    @Provides
    fun provideFreeBytes(@ApplicationContext context: Context): FreeBytes = FreeBytes {
        try {
            context.getSystemService(StorageManager::class.java).getAllocatableBytes(StorageManager.UUID_DEFAULT)
        } catch (_: IOException) {
            context.cacheDir.usableSpace   // what the file system says, when the storage service cannot answer
        }
    }

    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore
}
