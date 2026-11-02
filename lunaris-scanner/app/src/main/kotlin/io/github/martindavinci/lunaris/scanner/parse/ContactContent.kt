package io.github.martindavinci.lunaris.scanner.parse

import io.github.martindavinci.lunaris.scanner.model.ParsedContent

private const val VCARD = "VCARD"
private const val TEL_URI_PREFIX = "tel:"
private const val NAME_PART_SEPARATOR = " "
private const val LIST_SEPARATOR = ", "

/** N holds family;given;additional;prefix;suffix. These indexes put it in reading order. */
private val nameReadingOrder = listOf(3, 1, 2, 0, 4)

private val emptyContact = ParsedContent.Contact(
    name = null,
    organization = null,
    title = null,
    phones = emptyList(),
    emails = emptyList(),
    address = null,
    website = null,
    note = null,
)

/** vCard 2.1, 3.0 and 4.0 share this property syntax. Nothing usable means it is not a contact. */
internal fun parseVCard(text: String): ParsedContent.Contact? {
    val lines = parseContentLines(text)
    if (lines.firstOrNull()?.isBegin(VCARD) != true) return null
    val properties = lines.drop(1).takeWhile { !it.isEnd(VCARD) }
    return ParsedContent.Contact(
        name = properties.firstText("FN") ?: properties.firstStructured("N", ::readingOrderName),
        organization = properties.firstStructured("ORG", ::joinedList),
        title = properties.firstText("TITLE"),
        phones = properties.allTexts("TEL").map(::withoutTelScheme),
        emails = properties.allTexts("EMAIL"),
        address = properties.firstStructured("ADR", ::joinedList),
        website = properties.firstText("URL"),
        note = properties.firstText("NOTE"),
    ).takeUnless { it == emptyContact }
}

internal fun parseMeCard(body: String): ParsedContent.Contact? {
    val fields = parseFields(body)
    fun all(key: String) = fields.filter { it.key == key }.map { it.value.trim() }.filter { it.isNotEmpty() }
    fun first(key: String) = all(key).firstOrNull()
    return ParsedContent.Contact(
        name = fields.firstOrNull { it.key == "N" }?.let { meCardName(it.raw) },
        organization = first("ORG"),
        title = first("TITLE"),
        phones = all("TEL"),
        emails = all("EMAIL"),
        address = first("ADR"),
        website = first("URL"),
        note = first("NOTE"),
    ).takeUnless { it == emptyContact }
}

private fun withoutTelScheme(value: String): String =
    if (value.startsWith(TEL_URI_PREFIX, ignoreCase = true)) value.substring(TEL_URI_PREFIX.length) else value

private fun List<ContentLine>.firstStructured(name: String, read: (List<String>) -> String?): String? =
    firstNotNullOfOrNull { line ->
        if (line.name != name) return@firstNotNullOfOrNull null
        read(line.value.splitUnescaped(';').map { unescapeText(it).trim() })
    }

private fun readingOrderName(parts: List<String>): String? =
    nameReadingOrder.mapNotNull(parts::getOrNull).filter { it.isNotEmpty() }
        .joinToString(NAME_PART_SEPARATOR).ifEmpty { null }

private fun joinedList(parts: List<String>): String? =
    parts.filter { it.isNotEmpty() }.joinToString(LIST_SEPARATOR).ifEmpty { null }

/** MECARD names are `last,first`. */
private fun meCardName(raw: String): String? =
    raw.splitUnescaped(',').map { unescapeField(it).trim() }.filter { it.isNotEmpty() }.reversed()
        .joinToString(NAME_PART_SEPARATOR).ifEmpty { null }
