package io.github.martindavinci.lunaris.archives.safety

import java.text.Normalizer

/**
 * Spec rules 9 (one component made writable) and 10 (leaf name for the cache).
 *
 * The characters that reorder text (U+202A to U+202E, U+2066 to U+2069) also become '_': they let
 * a name ending in "fdp.exe" show as ending in "exe.pdf" in the app that receives the file.
 */
object SafeComponent {
    private const val FORBIDDEN = "\"*/:<>?\\|"
    private const val REPLACEMENT = '_'
    private const val LAST_CONTROL = 0x1F
    private const val DELETE = 0x7F
    private const val MAX_EXTENSION_CHARS = 16
    private val EMBEDDING_CONTROLS = 0x202A..0x202E
    private val ISOLATE_CONTROLS = 0x2066..0x2069

    fun forWriting(component: String): String? {
        val nfc = Normalizer.normalize(component, Normalizer.Form.NFC)
        val replaced = buildString(nfc.length) { nfc.forEach { c -> append(if (unsafe(c)) REPLACEMENT else c) } }
        val trimmed = replaced.trimEnd('.', ' ')
        if (trimmed.isEmpty() || trimmed == "." || trimmed == "..") return null
        return fitBytes(trimmed)
    }

    fun leaf(result: SafePath.Result.Ok): String? = forWriting(result.components.last())

    private fun unsafe(c: Char): Boolean =
        c in FORBIDDEN || c.code <= LAST_CONTROL || c.code == DELETE ||
            c.code in EMBEDDING_CONTROLS || c.code in ISOLATE_CONTROLS

    private fun fitBytes(name: String): String? {
        if (utf8Size(name) <= Limits.MAX_COMPONENT_BYTES) return name
        val dot = name.lastIndexOf('.')
        val ext = if (dot > 0 && name.length - dot <= MAX_EXTENSION_CHARS) name.substring(dot) else ""
        var base = name.substring(0, name.length - ext.length)
        while (base.isNotEmpty() && utf8Size(base) + utf8Size(ext) > Limits.MAX_COMPONENT_BYTES) {
            base = base.substring(0, base.offsetByCodePoints(base.length, -1))
        }
        // Without an extension the cut can end on a dot or a space again: trim it as rule 9 says.
        val cut = if (ext.isEmpty()) base.trimEnd('.', ' ') else base + ext
        return cut.ifEmpty { null }
    }

    private fun utf8Size(s: String) = s.toByteArray(Charsets.UTF_8).size
}
