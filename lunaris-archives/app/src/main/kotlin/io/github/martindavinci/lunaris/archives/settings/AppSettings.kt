package io.github.martindavinci.lunaris.archives.settings

import io.github.martindavinci.lunaris.archives.browse.SortOrder

data class AppSettings(val theme: ThemeMode = ThemeMode.SYSTEM, val rememberRecents: Boolean = true, val sort: SortOrder = SortOrder())
