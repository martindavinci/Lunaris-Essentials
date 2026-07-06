package tools.lunaris.texteditor.edit

import javax.inject.Inject

class SearchEngine @Inject constructor() {

    private fun regexFor(query: String, opts: SearchOptions): Regex? {
        if (query.isEmpty()) return null
        val flags = if (opts.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        val pattern = if (opts.regex) query else Regex.escape(query)
        return runCatching { Regex(pattern, flags) }.getOrNull()
    }

    /** Every match in [text], in order — used to show the "current/total" count during search. */
    fun findAll(text: String, query: String, opts: SearchOptions): List<MatchRange> {
        val re = regexFor(query, opts) ?: return emptyList()
        return re.findAll(text).map { MatchRange(it.range.first, it.range.last + 1) }.toList()
    }

    fun findNext(text: String, query: String, fromOffset: Int, opts: SearchOptions): MatchRange? {
        val re = regexFor(query, opts) ?: return null
        val start = fromOffset.coerceIn(0, text.length)
        re.find(text, start)?.let { return MatchRange(it.range.first, it.range.last + 1) }
        if (opts.wrap) re.find(text, 0)?.let {
            if (it.range.first < start) return MatchRange(it.range.first, it.range.last + 1)
        }
        return null
    }

    fun findPrevious(text: String, query: String, fromOffset: Int, opts: SearchOptions): MatchRange? {
        val re = regexFor(query, opts) ?: return null
        val limit = fromOffset.coerceIn(0, text.length)
        val before = re.findAll(text).lastOrNull { it.range.last + 1 <= limit }
        if (before != null) return MatchRange(before.range.first, before.range.last + 1)
        if (opts.wrap) re.findAll(text).lastOrNull()?.let { return MatchRange(it.range.first, it.range.last + 1) }
        return null
    }

    fun replaceAll(text: String, query: String, replacement: String, opts: SearchOptions): ReplaceResult {
        val re = regexFor(query, opts) ?: return ReplaceResult(text, 0)
        var count = 0
        val out = re.replace(text) { m ->
            count++
            if (opts.regex) substituteGroups(replacement, m) else replacement
        }
        return ReplaceResult(out, count)
    }

    private fun substituteGroups(replacement: String, m: MatchResult): String {
        val sb = StringBuilder()
        var i = 0
        while (i < replacement.length) {
            val c = replacement[i]
            if (c == '$' && i + 1 < replacement.length && replacement[i + 1].isDigit()) {
                val g = replacement[i + 1] - '0'
                sb.append(m.groupValues.getOrElse(g) { "" })
                i += 2
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }
}
