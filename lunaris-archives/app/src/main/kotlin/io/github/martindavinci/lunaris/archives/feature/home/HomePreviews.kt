package io.github.martindavinci.lunaris.archives.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.martindavinci.lunaris.archives.recents.RecentArchive
import io.github.martindavinci.lunaris.archives.settings.ThemeMode
import io.github.martindavinci.lunaris.archives.ui.theme.LunarisArchivesTheme

private const val MB = 1_000_000L
private const val DAY_MILLIS = 24L * 60 * 60 * 1000
private val Now = System.currentTimeMillis()

private val Notes = RecentArchive("content://a/1", "Project notes.zip", "ZIP", 12_600_000, 48, locked = false, lastOpenedMillis = Now, lastPath = listOf("docs"))
private val Photos = RecentArchive("content://a/2", "Holiday photos.7z", "7z", 1_800 * MB, 312, locked = false, lastOpenedMillis = Now - DAY_MILLIS, lastPath = null)
private val Manuals = RecentArchive("content://a/3", "Manuals.rar", "RAR", 86 * MB, 23, locked = true, lastOpenedMillis = Now - DAY_MILLIS, lastPath = null)
private val Backup = RecentArchive("content://a/4", "Source backup.tar.gz", "TAR.GZ", 42_300_000, 1_204, locked = false, lastOpenedMillis = Now - 14 * DAY_MILLIS, lastPath = null)
private val Listed = HomeUiState(entries = listOf(Photos, Manuals, Backup), continueItem = Notes)

@Composable
private fun Show(state: HomeUiState, theme: ThemeMode = ThemeMode.DARK) {
    LunarisArchivesTheme(theme) { HomeScreen(state, onAction = {}, onPickFile = {}, onCreate = {}, onSettings = {}) }
}

@Preview
@Composable
private fun HomePreview() = Show(Listed)

@Preview
@Composable
private fun HomeLightPreview() = Show(Listed, ThemeMode.LIGHT)

@Preview
@Composable
private fun HomeRowMenuPreview() = Show(Listed.copy(menuFor = Manuals.uri))

@Preview
@Composable
private fun HomeEmptyPreview() = Show(HomeUiState())

@Preview
@Composable
private fun HomeRecentsOffLightPreview() = Show(HomeUiState(rememberRecents = false), ThemeMode.LIGHT)

@Preview
@Composable
private fun HomeSearchPreview() = Show(Listed.copy(query = "man", entries = listOf(Manuals)))

@Preview
@Composable
private fun HomeSearchNoMatchPreview() = Show(Listed.copy(query = "invoice", entries = emptyList()))

@Preview
@Composable
private fun HomeClearPreview() = Show(Listed.copy(confirmClear = true))
