package io.github.martindavinci.lunaris.archives.browse

import java.util.Locale

/**
 * Case-insensitive natural order: "file2" < "file10"; ties broken case-sensitively for a stable order.
 * Sorting many rows: keep [key] beside each name and use the four-argument [compare].
 */
object NaturalOrder : Comparator<String> {
    fun key(name: String): String = name.lowercase(Locale.ROOT)

    override fun compare(a: String, b: String): Int = compare(a, key(a), b, key(b))

    fun compare(a: String, aKey: String, b: String, bKey: String): Int {
        val folded = compareFolded(aKey, bKey)
        return if (folded != 0) folded else a.compareTo(b)
    }

    private fun compareFolded(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            if (a[i].isAsciiDigit() && b[j].isAsciiDigit()) {
                val ei = digitsEnd(a, i)
                val ej = digitsEnd(b, j)
                val na = a.substring(i, ei).trimStart('0')
                val nb = b.substring(j, ej).trimStart('0')
                val byNumber = if (na.length != nb.length) na.length - nb.length else na.compareTo(nb)
                if (byNumber != 0) return byNumber
                val byZeros = (ei - i) - (ej - j)
                if (byZeros != 0) return byZeros
                i = ei
                j = ej
            } else {
                if (a[i] != b[j]) return a[i] - b[j]
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }

    private fun digitsEnd(s: String, from: Int): Int {
        var k = from
        while (k < s.length && s[k].isAsciiDigit()) k++
        return k
    }

    /** Only 0–9 count as numbers: with every Unicode digit ("１", "٣") the order is not transitive and the sort throws. */
    private fun Char.isAsciiDigit() = this in '0'..'9'
}
