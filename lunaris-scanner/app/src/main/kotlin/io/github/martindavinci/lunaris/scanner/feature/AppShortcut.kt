package io.github.martindavinci.lunaris.scanner.feature

/** What the app's icon offers when held down, as declared in res/xml/shortcuts.xml. */
enum class AppShortcut(val action: String) {
    SCAN_CODE("io.github.martindavinci.lunaris.scanner.action.SCAN_CODE"),
    SCAN_DOCUMENT("io.github.martindavinci.lunaris.scanner.action.SCAN_DOCUMENT"),
    HISTORY("io.github.martindavinci.lunaris.scanner.action.HISTORY");

    companion object {
        fun of(action: String?): AppShortcut? = entries.firstOrNull { it.action == action }
    }
}
