package tools.lunaris.texteditor.syntax

data class ColorSchemeDefinition(
    val id: String,
    val display: String,
    val themeAsset: String,
    val dark: Boolean,
)
