package io.github.martindavinci.lunaris.pdf.pages

import kotlin.math.abs

/** What a place in the new document holds. */
sealed interface SlotContent {
    data class PageOf(val source: Int, val index: Int) : SlotContent
    data class Blank(val width: Float, val height: Float) : SlotContent

    /** An image, turned as its EXIF says when it is added. */
    data class PictureOf(val source: Int, val turn: Int) : SlotContent
}

/** [rotation] is on top of what the page already has. */
data class PageSlot(val id: Long, val content: SlotContent, val rotation: Int = 0)

/** The pages as they stand, with their history. Nothing touches a file until the document is saved. */
class PageEdits private constructor(initial: List<PageSlot>, private var step: Int, private var nextId: Long) {

    constructor(initial: List<PageSlot>) : this(initial, 0, (initial.maxOfOrNull { it.id } ?: -1L) + 1)

    private val undos = ArrayDeque<List<PageSlot>>()
    private val redos = ArrayDeque<List<PageSlot>>()
    private var savedStep = 0

    var slots: List<PageSlot> = initial
        private set

    val canUndo: Boolean get() = undos.isNotEmpty()
    val canRedo: Boolean get() = redos.isNotEmpty()

    /** How many steps away from the last save this is. */
    val changes: Int get() = abs(step - savedStep)

    fun rotate(ids: Set<Long>) = change(slots.map { if (it.id in ids) it.copy(rotation = (it.rotation + QUARTER) % FULL_TURN) else it })

    /** False, with no change, if no page would be left. */
    fun delete(ids: Set<Long>): Boolean {
        val left = slots.filterNot { it.id in ids }
        if (left.isEmpty()) return false
        change(left)
        return true
    }

    /** [toIndex] is a place in the list as it is now, before the group leaves it. */
    fun move(ids: Set<Long>, toIndex: Int) {
        val at = toIndex.coerceIn(0, slots.size)
        val before = slots.take(at).filterNot { it.id in ids }
        val after = slots.drop(at).filterNot { it.id in ids }
        change(before + slots.filter { it.id in ids } + after)
    }

    /** The ids of the new slots. */
    fun insert(atIndex: Int, contents: List<SlotContent>): List<Long> {
        val added = contents.map { content ->
            PageSlot(nextId++, content, (content as? SlotContent.PictureOf)?.turn ?: 0)
        }
        val at = atIndex.coerceIn(0, slots.size)
        change(slots.take(at) + added + slots.drop(at))
        return added.map { it.id }
    }

    fun undo() {
        val previous = undos.removeLastOrNull() ?: return
        redos.addLast(slots)
        slots = previous
        step--
    }

    fun redo() {
        val next = redos.removeLastOrNull() ?: return
        undos.addLast(slots)
        slots = next
        step++
    }

    fun markSaved() {
        savedStep = step
    }

    /** The history stays out: the pages are what a process that dies must not lose. */
    fun encode(): String = listOf(nextId, changes, slots.joinToString(SLOT_END, transform = ::encodeSlot)).joinToString(FIELD_END)

    private fun change(new: List<PageSlot>) {
        if (new == slots) return
        undos.addLast(slots)
        if (undos.size > MAX_UNDO) undos.removeFirst()
        redos.clear()
        slots = new
        step++
    }

    private fun encodeSlot(slot: PageSlot): String = when (val content = slot.content) {
        is SlotContent.PageOf -> "${slot.id},p,${slot.rotation},${content.source},${content.index}"
        is SlotContent.Blank -> "${slot.id},b,${slot.rotation},${content.width},${content.height}"
        is SlotContent.PictureOf -> "${slot.id},i,${slot.rotation},${content.source},${content.turn}"
    }

    companion object {
        const val MAX_UNDO = 50
        private const val QUARTER = 90
        private const val FULL_TURN = 360
        private const val FIELD_END = "|"
        private const val SLOT_END = ";"
        private const val FIELDS = 3
        private const val SLOT_FIELDS = 5

        fun decode(text: String): PageEdits? {
            val fields = text.split(FIELD_END)
            if (fields.size != FIELDS) return null
            val nextId = fields[0].toLongOrNull() ?: return null
            val changes = fields[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            val slots = fields[2].split(SLOT_END).map { decodeSlot(it) ?: return null }
            return PageEdits(slots, changes, nextId)
        }

        private fun decodeSlot(text: String): PageSlot? {
            val parts = text.split(",")
            if (parts.size != SLOT_FIELDS) return null
            val id = parts[0].toLongOrNull() ?: return null
            val rotation = parts[2].toIntOrNull() ?: return null
            val content = when (parts[1]) {
                "p" -> SlotContent.PageOf(parts[3].toIntOrNull() ?: return null, parts[4].toIntOrNull() ?: return null)
                "b" -> SlotContent.Blank(parts[3].toFloatOrNull() ?: return null, parts[4].toFloatOrNull() ?: return null)
                "i" -> SlotContent.PictureOf(parts[3].toIntOrNull() ?: return null, parts[4].toIntOrNull() ?: return null)
                else -> return null
            }
            return PageSlot(id, content, rotation)
        }
    }
}
