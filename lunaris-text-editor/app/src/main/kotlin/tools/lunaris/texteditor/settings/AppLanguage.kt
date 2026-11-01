package tools.lunaris.texteditor.settings

/** The UI languages the app ships, as BCP-47 tags, and how a locale maps onto them. */
object AppLanguage {
    /** No choice made: the app follows the system language. */
    const val SYSTEM = ""

    /** The language of the unqualified `values/` resources. */
    const val DEFAULT = "en"

    /** Keep in step with `res/xml/locales_config.xml` and the `values-*` folders (checked by tests). */
    val supported = listOf("en", "de", "es", "fr", "it", "ja", "ko", "nl", "pl", "pt", "zh-CN")

    /**
     * Maps [tags] (one tag or a comma-separated list, as `LocaleList.toLanguageTags()` gives) to a
     * shipped language, matching on the language subtag when the exact tag is not shipped.
     * Anything else means [SYSTEM].
     */
    fun normalize(tags: String?): String {
        val tag = tags?.substringBefore(',')?.trim().orEmpty()
        if (tag.isEmpty()) return SYSTEM
        if (tag in supported) return tag
        val language = tag.substringBefore('-')
        return supported.firstOrNull { it.substringBefore('-') == language } ?: SYSTEM
    }
}
