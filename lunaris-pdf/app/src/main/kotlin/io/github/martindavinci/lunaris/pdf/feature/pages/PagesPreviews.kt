package io.github.martindavinci.lunaris.pdf.feature.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.martindavinci.lunaris.pdf.feature.work.JobKind
import io.github.martindavinci.lunaris.pdf.feature.work.WorkState
import io.github.martindavinci.lunaris.pdf.pages.PageSlot
import io.github.martindavinci.lunaris.pdf.pages.SlotContent
import io.github.martindavinci.lunaris.pdf.pages.SplitPlan
import io.github.martindavinci.lunaris.pdf.settings.ThemeMode
import io.github.martindavinci.lunaris.pdf.ui.theme.LunarisPdfTheme

private val Sample = PagesUiState(
    name = "The Light of the Moon.pdf",
    slots = List(12) { PageSlot(it.toLong(), SlotContent.Blank(595f, 842f), if (it == 6) 90 else 0) },
    changes = 2,
    canUndo = true,
    loading = false,
)

@Composable
private fun Screen(state: PagesUiState, theme: ThemeMode = ThemeMode.DARK) {
    LunarisPdfTheme(theme) {
        PagesScreen(
            state = state,
            thumbnail = { null },
            pickThumbnail = { _, _ -> null },
            onBack = {},
            onSave = {},
            onExtract = {},
            onSplit = {},
            onOtherPdf = {},
            onImages = {},
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun PagesPreview() = Screen(Sample)

@Preview
@Composable
private fun PagesLightPreview() = Screen(Sample, ThemeMode.LIGHT)

@Preview
@Composable
private fun PagesSelectedPreview() = Screen(Sample.copy(selected = setOf(2L, 4L, 5L)))

@Preview
@Composable
private fun PagesAddPreview() = Screen(Sample.copy(panel = PagesPanel.Add(9)))

@Preview
@Composable
private fun PagesSplitPreview() = Screen(Sample.copy(panel = PagesPanel.Split(SplitPlan(listOf("1-7", "8-13", "x")))))

@Preview
@Composable
private fun PagesProtectedPreview() = Screen(Sample.copy(problem = PagesProblem.PROTECTED, changes = 0))

@Preview
@Composable
private fun PagesDamagedPreview() = Screen(Sample.copy(problem = PagesProblem.DAMAGED, slots = emptyList(), changes = 0))

@Preview
@Composable
private fun PagesLeavePreview() = Screen(Sample.copy(askingToLeave = true, changes = 4))

@Preview
@Composable
private fun PagesSavedPreview() =
    Screen(Sample.copy(work = WorkState.Saved(JobKind.SAVE, "The Light of the Moon (edited).pdf", "Download", 25, 2_400_000, 1, "content://a")))
