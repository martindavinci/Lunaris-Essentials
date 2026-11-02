package io.github.martindavinci.lunaris.scanner.scan

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanModule {
    @Binds
    abstract fun bindImageDecoder(decoder: ContentImageDecoder): ImageDecoder
}
