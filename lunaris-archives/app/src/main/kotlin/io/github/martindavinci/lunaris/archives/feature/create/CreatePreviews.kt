package io.github.martindavinci.lunaris.archives.feature.create

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.martindavinci.lunaris.archives.create.CompressionLevel
import io.github.martindavinci.lunaris.archives.create.CreateItem
import io.github.martindavinci.lunaris.archives.create.CreateState
import io.github.martindavinci.lunaris.archives.create.FormatChoice
import io.github.martindavinci.lunaris.archives.create.Packing
import io.github.martindavinci.lunaris.archives.settings.ThemeMode
import io.github.martindavinci.lunaris.archives.ui.theme.LunarisArchivesTheme

private const val KB = 1_000L
private const val STRONG = 4
private val Items = listOf(
    CreateItem("1", "meeting 3 Nov.txt", isFolder = false, fileCount = 1, sizeBytes = 3 * KB),
    CreateItem("2", "plan.pdf", isFolder = false, fileCount = 1, sizeBytes = 1_200 * KB),
    CreateItem("3", "Photos", isFolder = true, fileCount = 12, sizeBytes = 2_000 * KB),
)
private val Picked = CreateUiState(
    items = Items, totalBytes = 3_203 * KB, name = "Project notes", level = CompressionLevel.NORMAL,
    canCreate = true, pickedFiles = true, pickedFolder = true,
)

@Composable
private fun Show(state: CreateUiState, theme: ThemeMode = ThemeMode.DARK) {
    LunarisArchivesTheme(theme) {
        CreateScreen(state, onAction = {}, onPickFiles = {}, onPickFolder = {}, onCreate = {}, onClose = {})
    }
}

@Preview
@Composable
private fun CreateEmptyPreview() = Show(CreateUiState())

@Preview
@Composable
private fun CreateEmptyLightPreview() = Show(CreateUiState(), ThemeMode.LIGHT)

@Preview(heightDp = 1100)
@Composable
private fun CreatePickedPreview() = Show(Picked)

@Preview(heightDp = 1100)
@Composable
private fun CreateTarPreview() = Show(Picked.copy(formatChoice = FormatChoice.TAR, packing = Packing.XZ))

@Preview(heightDp = 1300)
@Composable
private fun CreatePasswordPreview() = Show(Picked.copy(protect = true, strength = STRONG, mismatch = true, canCreate = false))

@Preview(heightDp = 1300)
@Composable
private fun CreatePasswordLightPreview() = Show(Picked.copy(protect = true, strength = STRONG, nameError = true, canCreate = false), ThemeMode.LIGHT)

@Preview
@Composable
private fun CreatingPreview() =
    Show(Picked.copy(state = CreateState.Running(added = 9, total = 14, current = "img/site-visit-07.jpg", doneBytes = 1_900 * KB, totalBytes = 3_203 * KB)))

@Preview
@Composable
private fun CreatedPreview() =
    Show(Picked.copy(state = CreateState.Finished("Project notes.zip", 2_900 * KB, "Documents", "content://demo/1", skipped = emptyList())))

@Preview
@Composable
private fun CreatedWithSkippedLightPreview() = Show(
    Picked.copy(state = CreateState.Finished("Project notes.zip", 2_900 * KB, "Documents", "content://demo/1", skipped = listOf("Shared sheet"))),
    ThemeMode.LIGHT,
)
