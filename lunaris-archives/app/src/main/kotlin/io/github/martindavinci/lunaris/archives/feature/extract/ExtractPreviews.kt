package io.github.martindavinci.lunaris.archives.feature.extract

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.martindavinci.lunaris.archives.archive.ArchiveEntry
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.archive.RawPath
import io.github.martindavinci.lunaris.archives.extract.Conflict
import io.github.martindavinci.lunaris.archives.extract.ExtractionState
import io.github.martindavinci.lunaris.archives.extract.PlannedFile
import io.github.martindavinci.lunaris.archives.extract.SkippedEntry
import io.github.martindavinci.lunaris.archives.safety.SafePath
import io.github.martindavinci.lunaris.archives.safety.SkipReason
import io.github.martindavinci.lunaris.archives.settings.ThemeMode
import io.github.martindavinci.lunaris.archives.ui.theme.LunarisArchivesTheme

private const val MB = 1_000_000L
private val Base = ExtractUiState(archiveName = "Project notes.zip", formatLabel = "ZIP", location = "Documents / Project")
private val Running = ExtractionState.Running(doneFiles = 31, totalFiles = 48, doneBytes = 8 * MB, totalBytes = 12 * MB, current = "img/site-visit-07.jpg")
private val Notes = PlannedFile(
    ArchiveEntry(0, RawPath.of("notes.md".toByteArray()), "notes.md", SafePath.Result.Ok(listOf("notes.md")), EntryType.FILE, 1, 1, 0, false, null),
    folder = emptyList(),
    name = "notes.md",
)

private fun done(skipped: List<SkippedEntry> = emptyList(), damaged: List<String> = emptyList(), written: Int = 48) =
    ExtractionState.Finished(written, 48, skipped, damaged, "Documents / Project", stoppedEarly = false)

@Composable
private fun Show(state: ExtractUiState, theme: ThemeMode = ThemeMode.DARK) {
    LunarisArchivesTheme(theme) { ExtractScreen(state, onAction = {}) }
}

@Preview
@Composable
private fun ExtractRunningPreview() = Show(Base.copy(state = Running))

@Preview
@Composable
private fun ExtractRunningLightPreview() = Show(Base.copy(state = Running), ThemeMode.LIGHT)

@Preview
@Composable
private fun ExtractConflictPreview() = Show(
    Base.copy(state = ExtractionState.Conflicts(List(6) { Conflict(Notes, "notes.md") }, current = 0), showApplyAll = true, keepBothName = "notes (1).md"),
)

@Preview
@Composable
private fun ExtractDonePreview() = Show(Base.copy(state = done()))

@Preview
@Composable
private fun ExtractDoneUnsafePreview() = Show(
    Base.copy(state = done(skipped = listOf(SkippedEntry("../../etc/hosts", SkipReason.OUTSIDE_FOLDER), SkippedEntry("C:\\Windows\\x", SkipReason.OUTSIDE_FOLDER)), written = 46)),
)

@Preview
@Composable
private fun ExtractDoneWithErrorsLightPreview() = Show(
    Base.copy(state = done(skipped = listOf(SkippedEntry("shortcut", SkipReason.LINK)), damaged = listOf("img/cover.png", "docs/old.md"), written = 45)),
    ThemeMode.LIGHT,
)

@Preview
@Composable
private fun ExtractStopPreview() = Show(Base.copy(state = Running, confirmStop = true))
