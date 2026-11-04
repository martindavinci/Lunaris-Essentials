package io.github.martindavinci.lunaris.pdf.feature.create

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.martindavinci.lunaris.pdf.convert.FileKind
import io.github.martindavinci.lunaris.pdf.feature.work.JobKind
import io.github.martindavinci.lunaris.pdf.feature.work.WorkState
import io.github.martindavinci.lunaris.pdf.settings.ThemeMode
import io.github.martindavinci.lunaris.pdf.ui.theme.LunarisPdfTheme

private val Mixed = listOf(
    CreateItem(0, "c://1", "The Light of the Moon.pdf", FileKind.PDF, 25),
    CreateItem(1, "c://2", "Crescent sketch.jpg", FileKind.IMAGE, 1),
    CreateItem(2, "c://3", "Observation log.md", FileKind.MARKDOWN, 3),
    CreateItem(3, "c://4", "Earthshine, handout for the evening class.html", FileKind.HTML, 2),
)

@Composable
private fun Screen(state: CreateUiState, theme: ThemeMode = ThemeMode.DARK) {
    LunarisPdfTheme(theme) {
        CreateScreen(state = state, thumbnail = { null }, onAddFiles = {}, onCreate = {}, onAction = {})
    }
}

@Preview
@Composable
private fun CreateEmptyPreview() = Screen(CreateUiState())

@Preview
@Composable
private fun CreateListPreview() = Screen(CreateUiState(items = Mixed))

@Preview
@Composable
private fun CreateLightPreview() = Screen(CreateUiState(items = Mixed), ThemeMode.LIGHT)

@Preview
@Composable
private fun CreateUnusablePreview() = Screen(
    CreateUiState(items = Mixed + CreateItem(4, "c://5", "Budget 2027.xlsx", FileKind.UNSUPPORTED, 0, FileProblem.UNSUPPORTED)),
)

@Preview
@Composable
private fun CreateLayoutPreview() = Screen(CreateUiState(items = Mixed, layoutOpen = true))

@Preview
@Composable
private fun CreateRunningPreview() = Screen(
    CreateUiState(items = Mixed, work = WorkState.Running(JobKind.CREATE, "Moon notes.pdf", "Download", null, 0.38f, preparing = 2, preparingOf = 4)),
)
