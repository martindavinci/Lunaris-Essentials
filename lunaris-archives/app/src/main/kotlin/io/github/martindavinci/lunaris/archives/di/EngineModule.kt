package io.github.martindavinci.lunaris.archives.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.martindavinci.lunaris.archives.engine.ArchiveEngine
import io.github.martindavinci.lunaris.archives.engine.EngineClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {
    @Binds
    @Singleton
    abstract fun bindArchiveEngine(client: EngineClient): ArchiveEngine
}
