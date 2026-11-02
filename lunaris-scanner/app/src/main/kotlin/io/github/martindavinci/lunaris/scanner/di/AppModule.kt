package io.github.martindavinci.lunaris.scanner.di

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
import io.github.martindavinci.lunaris.scanner.history.HistoryRepository
import io.github.martindavinci.lunaris.scanner.share.ScanRecorder
import javax.inject.Singleton

/** A damaged file starts over empty: otherwise the app could not get past its first screen. */
private val Context.dataStore by preferencesDataStore(
    name = "scanner_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore

    @Provides
    fun provideScanRecorder(history: HistoryRepository): ScanRecorder = ScanRecorder { history.add(it) }
}
