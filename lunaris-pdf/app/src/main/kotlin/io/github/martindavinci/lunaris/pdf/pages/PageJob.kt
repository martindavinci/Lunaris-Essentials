package io.github.martindavinci.lunaris.pdf.pages

import org.json.JSONArray
import org.json.JSONObject

enum class SourceKind(val wire: String) { PDF("pdf"), JPEG("jpeg"), PNG("png") }

/** The new files to make: for each, its pages in order. The page reads source n from a path of its own, so no uri or file name is in here. */
data class PageJob(val sources: List<SourceKind>, val outputs: List<List<PageSlot>>) {

    val pageCount: Int get() = outputs.sumOf { it.size }

    fun encode(): String = JSONObject()
        .put("sources", JSONArray(sources.map { it.wire }))
        .put("outputs", JSONArray(outputs.map { pages -> JSONArray(pages.map(::encodePage)) }))
        .toString()

    private fun encodePage(slot: PageSlot): JSONObject {
        val page = JSONObject().put("turn", slot.rotation)
        when (val content = slot.content) {
            is SlotContent.PageOf -> page.put("source", content.source).put("index", content.index)
            is SlotContent.Blank -> page.put("width", content.width.toDouble()).put("height", content.height.toDouble())
            is SlotContent.PictureOf -> page.put("source", content.source)
        }
        return page
    }
}
