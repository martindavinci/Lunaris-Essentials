package io.github.martindavinci.lunaris.archives.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.martindavinci.lunaris.archives.feature.browser.BrowserPreferences
import io.github.martindavinci.lunaris.archives.feature.browser.RecentsPort
import io.github.martindavinci.lunaris.archives.feature.browser.SourcesAdapter
import io.github.martindavinci.lunaris.archives.feature.browser.SourcesPort
import io.github.martindavinci.lunaris.archives.open.ArchiveOpener
import io.github.martindavinci.lunaris.archives.open.ReadCheck
import io.github.martindavinci.lunaris.archives.recents.ContentUriPermissions
import io.github.martindavinci.lunaris.archives.recents.RecentsRepository
import io.github.martindavinci.lunaris.archives.recents.RecentsStore
import io.github.martindavinci.lunaris.archives.recents.RecentsRecorder
import io.github.martindavinci.lunaris.archives.recents.SettingsSource
import io.github.martindavinci.lunaris.archives.recents.UriPermissions
import io.github.martindavinci.lunaris.archives.settings.SettingsStore
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Where CPU work off the main thread runs (Dispatchers.Default; a test dispatcher in tests). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Background

@Module
@InstallIn(SingletonComponent::class)
abstract class BrowserModule {
    @Binds
    abstract fun bindSources(adapter: SourcesAdapter): SourcesPort

    @Binds
    abstract fun bindPreferences(store: SettingsStore): BrowserPreferences

    @Binds
    abstract fun bindSettingsSource(store: SettingsStore): SettingsSource

    @Binds
    abstract fun bindRecents(recorder: RecentsRecorder): RecentsPort

    @Binds
    abstract fun bindPermissions(permissions: ContentUriPermissions): UriPermissions

    @Binds
    abstract fun bindRecentsRepository(store: RecentsStore): RecentsRepository

    @Binds
    abstract fun bindReadCheck(opener: ArchiveOpener): ReadCheck

    companion object {
        @Provides
        @Background
        fun provideBackground(): CoroutineDispatcher = Dispatchers.Default
    }
}
