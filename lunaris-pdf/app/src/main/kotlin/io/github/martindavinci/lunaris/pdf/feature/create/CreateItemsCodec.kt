package io.github.martindavinci.lunaris.pdf.feature.create

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.List
import io.github.martindavinci.lunaris.pdf.convert.FileKind

/** One line per row. A name or a uri holds no line end, and the tab goes first in a file name only if someone tries hard. */
internal object CreateItemsCodec {
    private const val LINE = "\n"
    private const val FIELD = "\t"
    private const val FIELDS = 6

    fun encode(items: List<CreateItem>): String = items.joinToString(LINE) { item ->
        listOf(item.id, item.uri, item.name.replace(FIELD, " "), item.kind.name, item.pages, item.problem?.name.orEmpty()).joinToString(FIELD)
    }

    fun decode(text: String): List<CreateItem> = text.split(LINE).mapNotNull { line ->
        val parts = line.split(FIELD)
        if (parts.size != FIELDS) return@mapNotNull null
        val id = parts[0].toLongOrNull() ?: return@mapNotNull null
        val kind = FileKind.entries.firstOrNull { it.name == parts[3] } ?: return@mapNotNull null
        CreateItem(id, parts[1], parts[2], kind, parts[4].toIntOrNull() ?: 0, FileProblem.entries.firstOrNull { it.name == parts[5] })
    }
}
