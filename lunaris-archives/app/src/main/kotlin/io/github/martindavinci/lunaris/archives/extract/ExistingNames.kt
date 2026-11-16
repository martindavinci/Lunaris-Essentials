package io.github.martindavinci.lunaris.archives.extract

/**
 * What a destination folder already holds, lower-cased with Locale.ROOT, files and folders apart:
 * a folder in the way of a file is never replaceable (replacing it could delete the folder).
 */
data class ExistingNames(val files: Set<String>, val folders: Set<String>) {
    operator fun contains(name: String): Boolean = name in files || name in folders
    val all: Set<String> get() = files + folders
}
