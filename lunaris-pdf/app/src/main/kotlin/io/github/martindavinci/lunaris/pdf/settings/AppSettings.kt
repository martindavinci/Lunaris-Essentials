package io.github.martindavinci.lunaris.pdf.settings



/** [nightPages]: pages are shown with their colours inverted, for reading in the dark. */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val nightPages: Boolean = false,
)
