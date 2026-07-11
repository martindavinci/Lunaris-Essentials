package tools.lunaris.texteditor.tts

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the concrete TTS engine so [DocumentReader] / ViewModels depend on the interface. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TtsModule {
    @Binds
    @Singleton
    abstract fun bindSpeechEngine(impl: DocumentSpeaker): SpeechEngine
}
