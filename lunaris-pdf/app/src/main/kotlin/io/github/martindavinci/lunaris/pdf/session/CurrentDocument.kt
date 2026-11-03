package io.github.martindavinci.lunaris.pdf.session

import io.github.martindavinci.lunaris.pdf.model.OpenDocument
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The document the Reader shows. Callers set it, then navigate. */
@Singleton
class CurrentDocument @Inject constructor() {
    private val current = MutableStateFlow<OpenDocument?>(null)

    val document: StateFlow<OpenDocument?> = current.asStateFlow()

    fun set(document: OpenDocument?) {
        current.value = document
    }
}
