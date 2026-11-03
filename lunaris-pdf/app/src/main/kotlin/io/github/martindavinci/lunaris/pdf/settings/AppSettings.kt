package io.github.martindavinci.lunaris.pdf.settings



/**
 * [nightPages]: pages are shown with their colours inverted, for reading in the dark.
 * [topButton]: the button back to the first page shows while reading.
 */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val nightPages: Boolean = false,
    val topButton: Boolean = true,
)
