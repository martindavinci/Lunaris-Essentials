package io.github.martindavinci.lunaris.scanner.feature.create

import io.github.martindavinci.lunaris.scanner.encode.CodeEncoder
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Encodes text into an image of about `sizePx` per side, or null when it cannot be encoded. */
fun interface CodeImageRenderer {
    suspend fun render(text: String, format: CodeFormat, sizePx: Int): CodeImage?
}

class ZxingCodeImageRenderer @Inject constructor() : CodeImageRenderer {
    override suspend fun render(text: String, format: CodeFormat, sizePx: Int): CodeImage? =
        withContext(Dispatchers.Default) {
            try {
                CodeImage.of(CodeEncoder.encode(text, format, sizePx), sizePx)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
}
