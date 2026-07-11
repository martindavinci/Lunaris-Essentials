package tools.lunaris.texteditor.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tools.lunaris.texteditor.data.ColorSchemeStore
import tools.lunaris.texteditor.data.EditorPreferences
import tools.lunaris.texteditor.data.ToolbarStore
import tools.lunaris.texteditor.data.TtsPrefsStore
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

    @Binds
    @Singleton
    abstract fun bindRecentsStore(impl: EditorPreferences): tools.lunaris.texteditor.data.RecentsStore

    @Binds
    @Singleton
    abstract fun bindSessionStore(impl: EditorPreferences): tools.lunaris.texteditor.data.SessionStore

    @Binds
    @Singleton
    abstract fun bindToolbarStore(impl: EditorPreferences): ToolbarStore

    @dagger.Binds @javax.inject.Singleton
    abstract fun bindAssociationStore(impl: tools.lunaris.texteditor.data.EditorPreferences): tools.lunaris.texteditor.data.AssociationStore

    @dagger.Binds @javax.inject.Singleton
    abstract fun bindSettingsStore(impl: tools.lunaris.texteditor.data.EditorPreferences): tools.lunaris.texteditor.settings.SettingsStore

    @dagger.Binds @javax.inject.Singleton
    abstract fun bindFileFilterStore(impl: tools.lunaris.texteditor.data.EditorPreferences): tools.lunaris.texteditor.data.FileFilterStore

    @Binds
    @Singleton
    abstract fun bindTtsPrefsStore(impl: EditorPreferences): TtsPrefsStore
}
