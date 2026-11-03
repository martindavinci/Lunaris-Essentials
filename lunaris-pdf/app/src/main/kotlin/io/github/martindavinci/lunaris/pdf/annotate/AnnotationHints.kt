package io.github.martindavinci.lunaris.pdf.annotate

import kotlinx.coroutines.flow.Flow

/** How many times the two finger hint has shown, so that it stops after a few. */
interface AnnotationHints {
    val shown: Flow<Int>
    suspend fun countOne()
}
