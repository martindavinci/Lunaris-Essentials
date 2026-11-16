package io.github.martindavinci.lunaris.archives.files

/** "primary:Documents/Project" → "Documents / Project"; null when the document id is not a storage path. */
object LocationLabel {
    private val VOLUME = Regex("primary|[0-9A-F]{4}-[0-9A-F]{4}")
    private const val SEPARATOR = " / "

    fun of(documentId: String?): String? {
        val colon = documentId?.indexOf(':') ?: return null
        if (colon < 0 || !VOLUME.matches(documentId.substring(0, colon))) return null
        val parts = documentId.substring(colon + 1).split('/').filter { it.isNotBlank() }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(SEPARATOR)
    }
}
