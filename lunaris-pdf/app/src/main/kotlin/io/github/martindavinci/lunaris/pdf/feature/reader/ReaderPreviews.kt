package io.github.martindavinci.lunaris.pdf.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.martindavinci.lunaris.pdf.ui.theme.LunarisPdfTheme
import io.github.martindavinci.lunaris.pdf.url.UrlWarning
import io.github.martindavinci.lunaris.pdf.viewer.OutlineItem

private val SampleState = ReaderUiState(
    name = "Codex Leicester.pdf",
    page = 12,
    pages = 72,
    loading = false,
    outline = listOf(OutlineItem("The light of the Moon", 12, 0)),
)

@Preview
@Composable
private fun ReaderScreenPreview() {
    LunarisPdfTheme {
        ReaderScreen(state = SampleState, onAction = {}, onBack = {}, onPrint = {}, onSaveCopy = {}, onSaveEdits = {}, document = {})
    }
}

@Preview
@Composable
private fun ReaderSearchPreview() {
    LunarisPdfTheme {
        ReaderScreen(
            state = SampleState.copy(search = SearchState("moon", current = 2, total = 9)),
            onAction = {},
            onBack = {},
            onPrint = {},
            onSaveCopy = {},
            onSaveEdits = {},
            document = {},
        )
    }
}

@Preview
@Composable
private fun ReaderErrorPreview() {
    LunarisPdfTheme {
        ReaderScreen(
            state = ReaderUiState(loading = false, error = ReaderError.CANNOT_OPEN),
            onAction = {},
            onBack = {},
            onPrint = {},
            onSaveCopy = {},
            onSaveEdits = {},
            document = {},
        )
    }
}

@Preview
@Composable
private fun ReaderLinkPreview() {
    LunarisPdfTheme {
        LinkDialog(
            link = PendingLink("http://example.com", "http://example.com", setOf(UrlWarning.NOT_HTTPS), canOpen = true),
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun ReaderPasswordPreview() {
    LunarisPdfTheme {
        PasswordDialog(PasswordPrompt(wrong = true), "Codex Leicester.pdf", onSubmit = {}, onCancel = {})
    }
}
