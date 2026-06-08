package tools.lunaris.texteditor.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tools.lunaris.texteditor.data.ColorSchemeStore
import tools.lunaris.texteditor.data.EditorPreferences
import tools.lunaris.texteditor.file.FileGateway
import tools.lunaris.texteditor.file.SafFileGateway
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindFileGateway(impl: SafFileGateway): FileGateway

    @Binds
    @Singleton
    abstract fun bindColorSchemeStore(impl: EditorPreferences): ColorSchemeStore
}
