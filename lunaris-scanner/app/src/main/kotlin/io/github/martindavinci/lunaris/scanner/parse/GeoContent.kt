package io.github.martindavinci.lunaris.scanner.parse

import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import kotlin.math.abs

private const val QUERY_KEY = "Q"
private const val MIN_COORDINATES = 2
private const val MAX_COORDINATES = 3
private const val MAX_LATITUDE = 90.0
private const val MAX_LONGITUDE = 180.0

/**
 * Plain decimals only: String.toDouble alone would also accept NaN, Infinity, 1e1 and 0x1p3. Not \d:
 * on Android it also matches digits of other scripts, which toDouble then refuses.
 */
private val decimal = Regex("""[+-]?([0-9]+(\.[0-9]*)?|\.[0-9]+)""")

/** `geo:lat,lon[,alt][;params][?q=query]`. In a query a plus means a space, as Android maps read it. */
internal fun parseGeo(body: String): ParsedContent.Geo? {
    val coordinates = body.substringBefore('?').substringBefore(';').split(',')
    if (coordinates.size !in MIN_COORDINATES..MAX_COORDINATES) return null
    if (!coordinates.all { decimal.matches(it) }) return null
    val latitude = coordinates[0].toDoubleOrNull() ?: return null
    val longitude = coordinates[1].toDoubleOrNull() ?: return null
    if (abs(latitude) > MAX_LATITUDE || abs(longitude) > MAX_LONGITUDE) return null
    val query = queryParameters(body.substringAfter('?', ""))[QUERY_KEY]
    return ParsedContent.Geo(latitude, longitude, query?.replace('+', ' ')?.let(::percentDecode)?.ifEmpty { null })
}
