package tools.lunaris.texteditor.syntax

/** One selectable language. [scopeName]/[grammarAsset] are null for plain text. */
data class LanguageDefinition(
    val id: String,
    val display: String,
    val scopeName: String?,
    val grammarAsset: String?,
    val extensions: List<String>,
)
