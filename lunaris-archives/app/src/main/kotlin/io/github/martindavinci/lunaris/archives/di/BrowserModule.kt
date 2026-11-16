package io.github.martindavinci.lunaris.archives.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.martindavinci.lunaris.archives.browse.SortOrder
import io.github.martindavinci.lunaris.archives.feature.browser.BrowserPreferences
import io.github.martindavinci.lunaris.archives.feature.browser.RecentsPort
import io.github.martindavinci.lunaris.archives.feature.browser.SourcesAdapter
import io.github.martindavinci.lunaris.archives.feature.browser.SourcesPort
import io.github.martindavinci.lunaris.archives.session.OpenArchive
import io.github.martindavinci.lunaris.archives.settings.AppSettings
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Where CPU work off the main thread runs (Dispatchers.Default; a test dispatcher in tests). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Background

@Module
@InstallIn(SingletonComponent::class)
abstract class BrowserModule {
    @Binds
    abstract fun bindSources(adapter: SourcesAdapter): SourcesPort

    companion object {
        @Provides
        @Background
        fun provideBackground(): CoroutineDispatcher = Dispatchers.Default

        // Until the settings and recents stores exist (B9): defaults and no memory.
        @Provides
        fun providePreferences(): BrowserPreferences = object : BrowserPreferences {
            override val settings: Flow<AppSettings> = flowOf(AppSettings())
            override suspend fun saveSort(order: SortOrder) = Unit
        }

        @Provides
        fun provideRecents(): RecentsPort = object : RecentsPort {
            override suspend fun visited(archive: OpenArchive, path: List<String>) = Unit
        }
    }
}
