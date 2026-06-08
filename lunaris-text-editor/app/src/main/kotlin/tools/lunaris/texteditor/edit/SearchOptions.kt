package tools.lunaris.texteditor.edit

data class SearchOptions(val caseSensitive: Boolean, val regex: Boolean, val wrap: Boolean)

data class ReplaceResult(val text: String, val count: Int)
