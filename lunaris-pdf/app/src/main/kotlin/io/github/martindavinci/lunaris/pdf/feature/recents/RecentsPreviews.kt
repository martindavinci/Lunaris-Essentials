package io.github.martindavinci.lunaris.pdf.feature.recents

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.martindavinci.lunaris.pdf.recents.RecentEntry
import io.github.martindavinci.lunaris.pdf.recents.RecentsHome
import io.github.martindavinci.lunaris.pdf.settings.ThemeMode
import io.github.martindavinci.lunaris.pdf.ui.theme.LunarisPdfTheme

private val Moon = RecentEntry("content://a/1", "The Light of the Moon.pdf", page = 12, pages = 25, chapter = "2. Leonardo")
private val Notes = RecentEntry("content://a/2", "Field notes.pdf", page = 1, pages = 8)
private val Rent = RecentEntry("content://a/3", "Rental agreement.pdf", page = 3, pages = 14, locked = true)

@Preview
@Composable
private fun RecentsPreview() {
    LunarisPdfTheme(ThemeMode.DARK) {
        Surface(color = MaterialTheme.colorScheme.background) {
            RecentsScreen(
                state = RecentsUiState(
                    entries = listOf(Moon, Notes, Rent),
                    home = RecentsHome(resume = Moon, all = listOf(Moon, Notes, Rent)),
                ),
                onAction = {},
                onOpenSettings = {},
                onPickFile = {},
                thumbnail = { null },
            )
        }
    }
}

@Preview
@Composable
private fun RecentsEmptyPreview() {
    LunarisPdfTheme(ThemeMode.DARK) {
        Surface(color = MaterialTheme.colorScheme.background) {
            RecentsScreen(state = RecentsUiState(), onAction = {}, onOpenSettings = {}, onPickFile = {}, thumbnail = { null })
        }
    }
}
