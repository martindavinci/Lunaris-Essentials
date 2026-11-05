package io.github.martindavinci.lunaris.scanner.document

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Hands the first photo of a scan from the Scan screen to the Document screen. Callers put it, then navigate. */
@Singleton
class DocumentInbox @Inject constructor() {
    private var photo: ImportedPhoto? = null

    fun put(photo: ImportedPhoto) {
        this.photo = photo
    }

    fun take(): ImportedPhoto? = photo.also { photo = null }
}

/** Renders a page for the screen. Null when it cannot, for one thing when memory runs out. */
fun interface PageRendering {
    suspend fun render(photo: PagePixels, settings: PageSettings, workSize: PageSize, longSide: Int): PagePixels?
}

class BackgroundPageRendering @Inject constructor() : PageRendering {
    override suspend fun render(photo: PagePixels, settings: PageSettings, workSize: PageSize, longSide: Int): PagePixels? =
        withContext(Dispatchers.Default) {
            try {
                renderPage(photo, settings, workSize, longSide)
            } catch (_: OutOfMemoryError) {
                null
            }
        }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DocumentModule {
    @Binds
    abstract fun bindPhotos(photos: ContentDocumentPhotos): DocumentPhotos

    @Binds
    abstract fun bindFiles(files: ContentDocumentFiles): DocumentFiles

    @Binds
    abstract fun bindRendering(rendering: BackgroundPageRendering): PageRendering
}
