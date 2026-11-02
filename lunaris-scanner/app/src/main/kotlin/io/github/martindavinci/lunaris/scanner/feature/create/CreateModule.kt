package io.github.martindavinci.lunaris.scanner.feature.create

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CreateModule {
    @Binds
    abstract fun bindRenderer(renderer: ZxingCodeImageRenderer): CodeImageRenderer

    @Binds
    abstract fun bindFiles(files: ContentCodeImageFiles): CodeImageFiles
}
