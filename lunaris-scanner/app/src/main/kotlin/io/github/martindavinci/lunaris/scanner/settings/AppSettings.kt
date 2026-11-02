package io.github.martindavinci.lunaris.scanner.settings

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val keepHistory: Boolean = true,
    val vibrateOnScan: Boolean = true,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }
