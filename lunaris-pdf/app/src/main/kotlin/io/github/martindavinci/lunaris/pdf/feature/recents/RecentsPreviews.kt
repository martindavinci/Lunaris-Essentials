package io.github.martindavinci.lunaris.pdf.feature.recents

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.martindavinci.lunaris.pdf.recents.RecentEntry
import io.github.martindavinci.lunaris.pdf.ui.theme.LunarisPdfTheme

@Preview
@Composable
private fun RecentsPreview() {
    LunarisPdfTheme {
        Surface {
            RecentsScreen(
                state = RecentsUiState(entries = listOf(RecentEntry("content://a/1", "Codex Leicester.pdf", page = 12))),
                onAction = {},
                onOpenSettings = {},
                onPickFile = {},
            )
        }
    }
}

@Preview
@Composable
private fun RecentsEmptyPreview() {
    LunarisPdfTheme {
        Surface { RecentsScreen(state = RecentsUiState(), onAction = {}, onOpenSettings = {}, onPickFile = {}) }
    }
}
