package io.github.martindavinci.lunaris.pdf.feature.pages

import io.github.martindavinci.lunaris.pdf.pages.ImagePages
import io.github.martindavinci.lunaris.pdf.pages.PagePictures
import io.github.martindavinci.lunaris.pdf.pages.PageSlot
import io.github.martindavinci.lunaris.pdf.pages.PdfOpening
import io.github.martindavinci.lunaris.pdf.pages.SlotContent
import io.github.martindavinci.lunaris.pdf.pages.SourceKind

/** The files a document's new pages may come from. Source 0 is the document itself. */
internal class PageSources private constructor(private val entries: MutableList<Pair<SourceKind, String>>) {
    constructor(document: String) : this(mutableListOf(SourceKind.PDF to document))

    val kinds: List<SourceKind> get() = entries.map { it.first }
    val uris: List<String> get() = entries.map { it.second }

    fun uriOf(source: Int): String = entries[source].second

    fun add(kind: SourceKind, uri: String): Int {
        entries += kind to uri
        return entries.lastIndex
    }

    fun encode(): String = entries.joinToString(LINE) { (kind, uri) -> kind.name + FIELD + uri }

    companion object {
        private const val LINE = "\n"
        private const val FIELD = "\t"

        fun decode(text: String?): PageSources? {
            text ?: return null
            val entries = text.split(LINE).map { line ->
                val kind = line.substringBefore(FIELD)
                val uri = line.substringAfter(FIELD, "")
                val known = SourceKind.entries.firstOrNull { it.name == kind } ?: return null
                known to uri
            }
            return PageSources(entries.toMutableList())
        }
    }
}

/** Where new pages come from: nothing else here touches the list of pages. */
internal class PagesAdding(
    private val pictures: PagePictures,
    private val images: ImagePages,
    private val sources: PageSources,
    private val update: ((PagesUiState) -> PagesUiState) -> Unit,
) {
    /** A blank page takes the size of its neighbour, or that of a printed page. */
    suspend fun blankLike(neighbour: PageSlot?): SlotContent.Blank {
        val size = when (val content = neighbour?.content) {
            is SlotContent.PageOf -> pictures.size(sources.uriOf(content.source), content.index)?.let { it.width to it.height }
            is SlotContent.Blank -> content.width to content.height
            else -> null
        } ?: (A4_WIDTH to A4_HEIGHT)
        return SlotContent.Blank(size.first, size.second)
    }

    /** Pages of the other document are offered in the grid, all chosen: most often all are wanted. */
    suspend fun chooseFrom(uri: String, insertAfter: Int) {
        when (val opening = pictures.open(uri)) {
            is PdfOpening.Pages -> {
                val source = sources.add(SourceKind.PDF, uri)
                val pick = PagePick(source, opening.count, (0 until opening.count).toSet(), insertAfter)
                update { it.copy(panel = null, pick = pick) }
            }
            PdfOpening.Protected -> update { it.copy(panel = null, notice = PagesNotice.OTHER_PROTECTED) }
            PdfOpening.Damaged -> update { it.copy(panel = null, notice = PagesNotice.OTHER_DAMAGED) }
        }
    }

    fun toggle(index: Int) = update { state ->
        val pick = state.pick ?: return@update state
        val chosen = if (index in pick.chosen) pick.chosen - index else pick.chosen + index
        state.copy(pick = pick.copy(chosen = chosen))
    }

    fun chooseAll() = update { state ->
        val pick = state.pick ?: return@update state
        state.copy(pick = pick.copy(chosen = (0 until pick.pageCount).toSet()))
    }

    fun confirm(pick: PagePick): List<SlotContent> = pick.chosen.sorted().map { SlotContent.PageOf(pick.source, it) }

    /** One page per image. Those that cannot be read stay out, and a message says so. */
    suspend fun images(uris: List<String>): List<SlotContent> {
        val added = uris.mapNotNull { uri ->
            images.prepare(uri)?.let { image -> SlotContent.PictureOf(sources.add(image.kind, image.uri), image.turn) }
        }
        if (added.size < uris.size) update { it.copy(notice = PagesNotice.IMAGE_FAILED) }
        return added
    }

    private companion object {
        const val A4_WIDTH = 595f
        const val A4_HEIGHT = 842f
    }
}
