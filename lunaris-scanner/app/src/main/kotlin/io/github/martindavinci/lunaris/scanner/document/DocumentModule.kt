package io.github.martindavinci.lunaris.scanner.document

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Renders a page for the screen. Null when it cannot, for one thing when memory runs out. */
fun interface PageRendering {
    suspend fun render(photo: PagePixels, settings: PageSettings, workSize: PageSize, longSide: Int): PagePixels?
}

/** Looks at a photo: for the page on it, and for how that page is best cleaned. */
interface PageReading {
    suspend fun find(photo: PagePixels): PageCorners?

    suspend fun suggestClean(photo: PagePixels, corners: PageCorners): CleanMode
}

private const val SUGGESTION_LONG_SIDE = 800

class BackgroundPageReading @Inject constructor() : PageReading {
    override suspend fun find(photo: PagePixels): PageCorners? = withContext(Dispatchers.Default) {
        try {
            findPage(photo)
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    override suspend fun suggestClean(photo: PagePixels, corners: PageCorners): CleanMode = withContext(Dispatchers.Default) {
        try {
            val settings = PageSettings(corners = corners, clean = CleanMode.COLOUR)
            suggestedClean(renderPage(photo, settings, PageSize(photo.width, photo.height), SUGGESTION_LONG_SIDE))
        } catch (_: OutOfMemoryError) {
            CleanMode.COLOUR
        }
    }
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

    @Binds
    abstract fun bindReading(reading: BackgroundPageReading): PageReading
}
