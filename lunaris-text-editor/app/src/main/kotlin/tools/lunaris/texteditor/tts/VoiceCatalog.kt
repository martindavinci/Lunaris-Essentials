package tools.lunaris.texteditor.tts

import java.util.Locale

/** Turns raw [VoiceInfo]s into language-grouped, labeled [VoiceOption]s. Android voices have no
 *  friendly name or gender, so rows read "<locale> · <variant> · <offline|network>". */
object VoiceCatalog {
    fun group(voices: List<VoiceInfo>, offlineLabel: String, networkLabel: String): List<VoiceGroup> =
        voices
            .groupBy { it.locale.displayLanguage.replaceFirstChar { c -> c.uppercase() } }
            .toSortedMap()
            .map { (language, list) ->
                VoiceGroup(
                    language = language,
                    voices = list
                        .map { VoiceOption(it.id, label(it, offlineLabel, networkLabel), it.isNetwork) }
                        .sortedBy { it.label },
                )
            }

    private fun label(info: VoiceInfo, offlineLabel: String, networkLabel: String): String {
        val locale = info.locale.getDisplayName(info.locale).replaceFirstChar { it.uppercase() }
        val connectivity = if (info.isNetwork) networkLabel else offlineLabel
        return listOfNotNull(locale, variantOf(info.id), connectivity).joinToString(" · ")
    }

    /** Google voice ids look like "it-it-x-itc-local"; the token after "-x-" (minus the
     *  -local/-network suffix) is the variant. Null when the id has no such token. */
    private fun variantOf(id: String): String? {
        val marker = id.indexOf("-x-")
        if (marker == -1) return null
        val v = id.substring(marker + 3).removeSuffix("-local").removeSuffix("-network")
        return v.ifBlank { null }
    }
}
