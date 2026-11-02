package io.github.martindavinci.lunaris.scanner.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import javax.inject.Inject
import javax.inject.Singleton

/** The code the Result screen shows. Callers set it, then navigate. */
@Singleton
class CurrentResult @Inject constructor() {
    private val current = MutableStateFlow<ScannedCode?>(null)

    val code: StateFlow<ScannedCode?> = current.asStateFlow()

    fun set(code: ScannedCode?) {
        current.value = code
    }
}
