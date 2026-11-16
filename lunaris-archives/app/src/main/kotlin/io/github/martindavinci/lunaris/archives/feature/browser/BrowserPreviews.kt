package io.github.martindavinci.lunaris.archives.feature.browser

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.martindavinci.lunaris.archives.archive.ArchiveEntry
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.archive.RawPath
import io.github.martindavinci.lunaris.archives.browse.FolderRow
import io.github.martindavinci.lunaris.archives.browse.SearchHit
import io.github.martindavinci.lunaris.archives.engine.ArchiveProblem
import io.github.martindavinci.lunaris.archives.safety.SafePath
import io.github.martindavinci.lunaris.archives.settings.ThemeMode
import io.github.martindavinci.lunaris.archives.ui.CrumbSegment
import io.github.martindavinci.lunaris.archives.ui.theme.LunarisArchivesTheme

private const val ARCHIVE = "Project notes.zip"
private const val DEMO_TIME = 1_793_731_200_000L      // 3 Nov 2026
private const val KB = 1_000L
private const val OPENING_FRACTION = 0.64f

private fun file(index: Long, name: String, size: Long, locked: Boolean = false): FolderRow {
    val entry = ArchiveEntry(index, RawPath.of(name.toByteArray()), name, SafePath.Result.Ok(listOf(name)), EntryType.FILE, size, size, DEMO_TIME, locked, null)
    return FolderRow(name, listOf(name), isFolder = false, entry = entry, childCount = 0, totalSize = size)
}

private fun folder(name: String, items: Int) = FolderRow(name, listOf(name), isFolder = true, entry = null, childCount = items, totalSize = 0)

private val Budget = file(0, "budget.csv", 18 * KB)
private val Cover = file(1, "cover.png", 640 * KB)
private val Plan = file(2, "plan.pdf", 1_200 * KB)
private val Todo = file(4, "todo.txt", 1 * KB)
private val Root = listOf(folder("docs", 5), folder("drafts", 3), folder("img", 14), Budget, Cover, Plan, file(3, "README.md", 4 * KB), Todo)

private val Listed = BrowserUiState(
    title = ARCHIVE,
    badge = "ZIP",
    header = ArchiveHeader("1", ARCHIVE, "ZIP", 48, 6, 12_600 * KB, 9_800 * KB, Encryption.NONE, null, null),
    loading = LoadState.Ready,
    crumbs = listOf(CrumbSegment(ARCHIVE, isArchive = true)),
    rows = Root,
)

@Composable
private fun Show(state: BrowserUiState, theme: ThemeMode = ThemeMode.DARK) {
    LunarisArchivesTheme(theme) { BrowserScreen(state, onAction = {}) }
}

@Preview
@Composable
private fun BrowserRootPreview() = Show(Listed.copy(hiddenUnsafe = 2))

@Preview
@Composable
private fun BrowserRootLightPreview() = Show(Listed, ThemeMode.LIGHT)

@Preview
@Composable
private fun BrowserOpeningPreview() = Show(Listed.copy(opening = OpeningState("plan.pdf", OPENING_FRACTION)))

@Preview
@Composable
private fun BrowserSelectionPreview() =
    Show(Listed.copy(selection = setOf(0L, 1L, 4L), canShareSelection = true))

@Preview
@Composable
private fun BrowserSelectionLightPreview() =
    Show(Listed.copy(selection = setOf(0L, 1L, 4L), canShareSelection = true), ThemeMode.LIGHT)

@Preview
@Composable
private fun BrowserSearchPreview() =
    Show(Listed.copy(query = "plan", hits = listOf(SearchHit(Plan, 0..3))))

@Preview
@Composable
private fun BrowserReadingPreview() =
    Show(BrowserUiState(title = "Source backup.tar.gz", badge = "TAR.GZ", loading = LoadState.ReadingSlow(1_204), crumbs = listOf(CrumbSegment("Source backup.tar.gz", true))))

@Preview
@Composable
private fun BrowserPasswordPreview() =
    Show(BrowserUiState(title = "Manuals.rar", badge = "RAR", password = PasswordPrompt("Manuals.rar", PasswordTarget.ARCHIVE)))

@Preview
@Composable
private fun BrowserPasswordWrongPreview() =
    Show(BrowserUiState(title = "Manuals.rar", badge = "RAR", password = PasswordPrompt("Manuals.rar", PasswordTarget.ARCHIVE, wrong = true)), ThemeMode.LIGHT)

@Preview
@Composable
private fun BrowserPasswordCheckingPreview() =
    Show(BrowserUiState(title = "Manuals.rar", badge = "RAR", password = PasswordPrompt("Manuals.rar", PasswordTarget.ENTRY, checking = true)))

@Preview
@Composable
private fun BrowserRar4Preview() =
    Show(BrowserUiState(title = "Old backup.rar", badge = "RAR", problem = ArchiveProblem.RAR4_PASSWORD))

@Preview
@Composable
private fun BrowserDamagedLightPreview() =
    Show(BrowserUiState(title = "Photos.zip", badge = "ZIP", problem = ArchiveProblem.DAMAGED, partial = true), ThemeMode.LIGHT)
