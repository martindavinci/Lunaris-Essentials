package io.github.martindavinci.lunaris.pdf.tts

import java.util.Locale

/** A voice to choose. [variant] tells apart the voices of one language, which Android gives no names. */
data class VoiceOption(val id: String, val locale: Locale, val variant: String?, val isNetwork: Boolean)

data class VoiceGroup(val locale: Locale, val voices: List<VoiceOption>)

/** The voices of the device by language, the one in use first: phones carry hundreds of them. */
object VoiceCatalog {
    private const val VARIANT_MARK = "-x-"
    private const val LOCAL_SUFFIX = "-local"
    private const val NETWORK_SUFFIX = "-network"

    fun group(voices: List<VoiceInfo>, preferred: Locale): List<VoiceGroup> = voices
        .groupBy { it.locale.language }
        .map { (_, list) ->
            VoiceGroup(
                locale = list.first().locale,
                voices = list
                    .map { VoiceOption(it.id, it.locale, variantOf(it.id), it.isNetwork) }
                    .sortedWith(compareBy({ it.isNetwork }, { it.locale.toLanguageTag() }, { it.id })),
            )
        }
        .sortedWith(compareBy({ it.locale.language != preferred.language }, { it.locale.getDisplayLanguage(preferred) }))

    /** Google's ids look like "it-it-x-itc-local". */
    private fun variantOf(id: String): String? {
        val mark = id.indexOf(VARIANT_MARK)
        if (mark < 0) return null
        return id.substring(mark + VARIANT_MARK.length).removeSuffix(LOCAL_SUFFIX).removeSuffix(NETWORK_SUFFIX).ifBlank { null }
    }
}
