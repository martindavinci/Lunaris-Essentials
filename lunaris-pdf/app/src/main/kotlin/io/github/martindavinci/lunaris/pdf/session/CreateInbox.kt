package io.github.martindavinci.lunaris.pdf.session

import javax.inject.Inject
import javax.inject.Singleton

/** Files shared to the app, waiting for the Create PDF screen. */
@Singleton
class CreateInbox @Inject constructor() {
    private var waiting = emptyList<String>()

    fun put(uris: List<String>) {
        waiting = uris
    }

    fun take(): List<String> = waiting.also { waiting = emptyList() }
}
