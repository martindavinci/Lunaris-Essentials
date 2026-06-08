package tools.lunaris.texteditor.edit

object ColorLiteral {
    /** Formats the low 24 bits of [rgb] as "#RRGGBB" (uppercase). */
    fun hex(rgb: Int): String = "#%06X".format(rgb and 0xFFFFFF)
}
