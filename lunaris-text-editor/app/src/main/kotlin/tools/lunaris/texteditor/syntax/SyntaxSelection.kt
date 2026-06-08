package tools.lunaris.texteditor.syntax

/** How the editor decides which grammar to apply. */
sealed interface SyntaxSelection {
    data object Auto : SyntaxSelection
    data object None : SyntaxSelection
    data class Explicit(val languageId: String) : SyntaxSelection
}
