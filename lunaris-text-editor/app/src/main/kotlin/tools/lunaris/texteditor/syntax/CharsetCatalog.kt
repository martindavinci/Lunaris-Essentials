package tools.lunaris.texteditor.syntax

import java.nio.charset.Charset
import javax.inject.Inject

data class CharsetEntry(val display: String, val charset: Charset)

/** The selectable encodings (UTF-8 first). Only charsets supported on this JVM are kept. */
class CharsetCatalog @Inject constructor() {

    val entries: List<CharsetEntry> = candidates
        .filter { Charset.isSupported(it.second) }
        .map { CharsetEntry(it.first, Charset.forName(it.second)) }

    private val byName = entries.associateBy { it.display }

    fun findByName(display: String): CharsetEntry? = byName[display]

    companion object {
        // display name -> JVM charset name. UTF-8 must stay first.
        private val candidates = listOf(
            "UTF-8" to "UTF-8",
            "UTF-16" to "UTF-16",
            "UTF-16BE" to "UTF-16BE",
            "UTF-16LE" to "UTF-16LE",
            "UTF-32" to "UTF-32",
            "US-ASCII" to "US-ASCII",
            "ISO-8859-1 (Western)" to "ISO-8859-1",
            "ISO-8859-2 (Central European)" to "ISO-8859-2",
            "ISO-8859-5 (Cyrillic)" to "ISO-8859-5",
            "ISO-8859-6 (Arabic)" to "ISO-8859-6",
            "ISO-8859-7 (Greek)" to "ISO-8859-7",
            "ISO-8859-8 (Hebrew)" to "ISO-8859-8",
            "ISO-8859-9 (Turkish)" to "ISO-8859-9",
            "ISO-8859-13 (Baltic)" to "ISO-8859-13",
            "ISO-8859-15 (Western)" to "ISO-8859-15",
            "windows-1250 (Central European)" to "windows-1250",
            "windows-1251 (Cyrillic)" to "windows-1251",
            "windows-1252 (Western)" to "windows-1252",
            "windows-1253 (Greek)" to "windows-1253",
            "windows-1254 (Turkish)" to "windows-1254",
            "windows-1256 (Arabic)" to "windows-1256",
            "windows-1257 (Baltic)" to "windows-1257",
            "Big5 (Traditional Chinese)" to "Big5",
            "GBK (Simplified Chinese)" to "GBK",
            "GB18030 (Simplified Chinese)" to "GB18030",
            "Shift_JIS (Japanese)" to "Shift_JIS",
            "EUC-JP (Japanese)" to "EUC-JP",
            "EUC-KR (Korean)" to "EUC-KR",
            "TIS-620 (Thai)" to "TIS-620",
            "windows-1258 (Vietnamese)" to "windows-1258",
        )
    }
}
