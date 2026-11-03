package io.github.martindavinci.lunaris.pdf.viewer

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** What the app asks of the viewer page. */
sealed interface ViewerCommand {
    data class Open(val password: String?, val page: Int, val scale: String) : ViewerCommand
    data class GoToPage(val page: Int) : ViewerCommand
    data class Find(
        val query: String,
        val previous: Boolean,
        val caseSensitive: Boolean = false,
        val wholeWords: Boolean = false,
    ) : ViewerCommand
    data object CloseFind : ViewerCommand
    data class SetNight(val on: Boolean) : ViewerCommand

    /** How much of the top and bottom the app's bars cover, in dp. */
    data class SetBarSpace(val top: Int, val bottom: Int) : ViewerCommand
    data object MakeThumbnail : ViewerCommand

    /** The tool in hand; null ends the editing. */
    data class SetTool(val spec: ToolSpec?) : ViewerCommand
    data object UndoEdit : ViewerCommand
    data object RedoEdit : ViewerCommand
    data object DeleteSelected : ViewerCommand

    /** Commits what is being edited and answers with [ViewerEvent.EditsSettled]. */
    data object SettleEdits : ViewerCommand

    /** The next tap on a page puts this picture there, as base64 PNG. */
    data class ArmSignature(val png: String) : ViewerCommand
    data class SetNoteText(val id: String, val text: String) : ViewerCommand
    data class RemoveNote(val id: String) : ViewerCommand
    data object ExportDocument : ViewerCommand

    /** The piece was written: the page may send the next one. */
    data class ExportAck(val index: Int) : ViewerCommand

    /** What is in the document now counts as saved. */
    data object MarkSaved : ViewerCommand
}

/** What the viewer page tells the app. It renders untrusted files, so nothing here is taken on trust. */
sealed interface ViewerEvent {
    /** The page has loaded and waits for [ViewerCommand.Open]. */
    data object Ready : ViewerEvent
    data class Loaded(val pages: Int, val outline: List<OutlineItem>) : ViewerEvent
    data class PageChanged(val page: Int, val scale: String) : ViewerEvent
    data class PasswordNeeded(val wrong: Boolean) : ViewerEvent
    data object LoadFailed : ViewerEvent

    /** The system took the page's process back to free memory, as it can while the app is in the background. */
    data object RendererKilled : ViewerEvent

    /** [page] holds the current match, and [onPage] is how many matches that page has. */
    data class FindResult(
        val current: Int,
        val total: Int,
        val done: Boolean,
        val page: Int = 0,
        val onPage: Int = 0,
    ) : ViewerEvent
    data class LinkTapped(val url: String) : ViewerEvent
    data object Tapped : ViewerEvent

    /** [moving]: scrolled a moment ago. [down]: towards the end, or dragging the scroller. */
    data class ScrollChanged(val moving: Boolean, val down: Boolean) : ViewerEvent

    /** The page moved through the scroller or a link inside the document. */
    data class Jumped(val from: Int, val to: Int) : ViewerEvent
    data object ChapterCrossed : ViewerEvent

    /** The first page as a base64 JPEG. */
    data class Thumbnail(val jpeg: String) : ViewerEvent

    /** PasswordNeeded and the like aside, what concerns changing the document. */
    data class EditsChanged(val changes: Int, val pages: Int, val canUndo: Boolean, val canRedo: Boolean) : Editing

    /** After [ViewerCommand.SettleEdits]. [signed]: the document has a digital signature, which saving invalidates. */
    data class EditsSettled(val changes: Int, val pages: Int, val signed: Boolean) : Editing
    data class NoteRequested(val id: String, val text: String) : Editing
    data object SignaturePlaced : Editing

    /** The highlighter was dragged where the page has no text. */
    data object NoTextHere : Editing
    data class ExportChunk(val index: Int, val total: Int, val data: String) : Editing
    data class ExportDone(val bytes: Long) : Editing
    data object ExportFailed : Editing

    sealed interface Editing : ViewerEvent
}

/** A tool for the page. [size] is a thickness or a text size, as the tool needs. */
data class ToolSpec(val tool: String, val color: String, val size: Int)

/** [page] is null when the entry does not lead to a page of this document. */
data class OutlineItem(val title: String, val page: Int?, val depth: Int)

object ViewerProtocol {
    const val MAX_THUMBNAIL_CHARS = 2_000_000

    /** A megabyte of file in base64, and some room. */
    const val MAX_CHUNK_CHARS = 2_000_000
    private const val MIN_SCALE = 0.1
    private const val MAX_SCALE = 25.0
    private val NAMED_SCALES = setOf("page-width", "page-fit", "page-actual", "auto")

    fun isScale(text: String): Boolean =
        text in NAMED_SCALES || text.toDoubleOrNull()?.let { it in MIN_SCALE..MAX_SCALE } == true

    /** A JS call. Quotes and line breaks are escaped, so the text of a query stays text. */
    fun encode(command: ViewerCommand): String {
        val payload = when (command) {
            is ViewerCommand.Open -> JSONObject().put("type", "open").put("password", command.password)
                .put("page", command.page).put("scale", command.scale)
            is ViewerCommand.GoToPage -> JSONObject().put("type", "goToPage").put("page", command.page)
            is ViewerCommand.Find -> JSONObject().put("type", "find").put("query", command.query)
                .put("previous", command.previous)
                .put("caseSensitive", command.caseSensitive).put("wholeWords", command.wholeWords)
            ViewerCommand.CloseFind -> JSONObject().put("type", "closeFind")
            is ViewerCommand.SetNight -> JSONObject().put("type", "setNight").put("on", command.on)
            is ViewerCommand.SetBarSpace -> JSONObject().put("type", "setBarSpace")
                .put("top", command.top).put("bottom", command.bottom)
            ViewerCommand.MakeThumbnail -> JSONObject().put("type", "makeThumbnail")
            is ViewerCommand.SetTool -> JSONObject().put("type", "setTool")
                .put("tool", command.spec?.tool ?: JSONObject.NULL)
                .put("color", command.spec?.color ?: JSONObject.NULL)
                .put("size", command.spec?.size ?: 0)
            ViewerCommand.UndoEdit -> JSONObject().put("type", "undoEdit")
            ViewerCommand.RedoEdit -> JSONObject().put("type", "redoEdit")
            ViewerCommand.DeleteSelected -> JSONObject().put("type", "deleteSelected")
            ViewerCommand.SettleEdits -> JSONObject().put("type", "settleEdits")
            is ViewerCommand.ArmSignature -> JSONObject().put("type", "armSignature").put("png", command.png)
            is ViewerCommand.SetNoteText -> JSONObject().put("type", "setNoteText").put("id", command.id).put("text", command.text)
            is ViewerCommand.RemoveNote -> JSONObject().put("type", "removeNote").put("id", command.id)
            ViewerCommand.ExportDocument -> JSONObject().put("type", "exportDocument")
            is ViewerCommand.ExportAck -> JSONObject().put("type", "exportAck").put("index", command.index)
            ViewerCommand.MarkSaved -> JSONObject().put("type", "markSaved")
        }
        val safe = payload.toString()
            .replace("'", "\\u0027")
            .replace("<", "\\u003C")
            .replace(" ", "\\u2028")
            .replace(" ", "\\u2029")
        return "window.lunaris.handle($safe)"
    }

    /** Null for anything unknown, malformed or out of range. */
    fun decode(json: String): ViewerEvent? = try {
        val message = JSONObject(json)
        when (message.optString("type")) {
            "ready" -> ViewerEvent.Ready
            "tapped" -> ViewerEvent.Tapped
            "loadFailed" -> ViewerEvent.LoadFailed
            "loaded" -> loaded(message)
            "pageChanged" -> pageChanged(message)
            "passwordNeeded" -> ViewerEvent.PasswordNeeded(message.getBoolean("wrong"))
            "findResult" -> ViewerEvent.FindResult(
                message.getInt("current"),
                message.getInt("total"),
                message.getBoolean("done"),
                message.optInt("page", 0),
                message.optInt("onPage", 0),
            )
            "linkTapped" -> ViewerEvent.LinkTapped(message.getString("url"))
            "scrollChanged" -> ViewerEvent.ScrollChanged(message.getBoolean("moving"), message.getBoolean("down"))
            "chapterCrossed" -> ViewerEvent.ChapterCrossed
            "jumped" -> jumped(message)
            "thumbnail" -> message.getString("jpeg").takeIf { it.length <= MAX_THUMBNAIL_CHARS }?.let(ViewerEvent::Thumbnail)
            else -> editing(message)
        }
    } catch (_: JSONException) {
        null
    }

    private fun editing(message: JSONObject): ViewerEvent? =
        when (message.optString("type")) {
            "editsChanged" -> ViewerEvent.EditsChanged(
                message.getInt("changes"),
                message.getInt("pages"),
                message.getBoolean("canUndo"),
                message.getBoolean("canRedo"),
            ).takeIf { it.changes >= 0 && it.pages >= 0 }
            "editsSettled" -> ViewerEvent.EditsSettled(message.getInt("changes"), message.getInt("pages"), message.getBoolean("signed"))
                .takeIf { it.changes >= 0 && it.pages >= 0 }
            "noteRequested" -> ViewerEvent.NoteRequested(message.getString("id"), message.getString("text"))
            "signaturePlaced" -> ViewerEvent.SignaturePlaced
            "noTextHere" -> ViewerEvent.NoTextHere
            "exportChunk" -> exportChunk(message)
            "exportDone" -> message.getLong("bytes").takeIf { it >= 0 }?.let(ViewerEvent::ExportDone)
            "exportFailed" -> ViewerEvent.ExportFailed
            else -> null
        }

    private fun exportChunk(message: JSONObject): ViewerEvent? {
        val index = message.getInt("index")
        val total = message.getInt("total")
        val data = message.getString("data")
        return ViewerEvent.ExportChunk(index, total, data).takeIf { index in 0 until total && data.length <= MAX_CHUNK_CHARS }
    }

    private fun jumped(message: JSONObject): ViewerEvent? {
        val from = message.getInt("from")
        val to = message.getInt("to")
        return if (from > 0 && to > 0) ViewerEvent.Jumped(from, to) else null
    }

    private fun loaded(message: JSONObject): ViewerEvent? {
        val pages = message.getInt("pages")
        if (pages <= 0) return null
        val outline = message.optJSONArray("outline") ?: JSONArray()
        return ViewerEvent.Loaded(pages, List(outline.length()) { outlineItem(outline.getJSONObject(it)) })
    }

    private fun outlineItem(item: JSONObject) = OutlineItem(
        title = item.getString("title"),
        page = item.optInt("page", 0).takeIf { it > 0 },
        depth = item.optInt("depth", 0).coerceAtLeast(0),
    )

    private fun pageChanged(message: JSONObject): ViewerEvent? {
        val page = message.getInt("page")
        val scale = message.getString("scale")
        return if (page > 0 && isScale(scale)) ViewerEvent.PageChanged(page, scale) else null
    }
}
