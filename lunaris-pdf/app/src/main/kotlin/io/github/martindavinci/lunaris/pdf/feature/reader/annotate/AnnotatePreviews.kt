package io.github.martindavinci.lunaris.pdf.feature.reader.annotate

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.martindavinci.lunaris.pdf.annotate.AnnotationTool
import io.github.martindavinci.lunaris.pdf.annotate.SavedSignature
import io.github.martindavinci.lunaris.pdf.feature.reader.AnnotationUiState
import io.github.martindavinci.lunaris.pdf.feature.reader.DocumentEdits
import io.github.martindavinci.lunaris.pdf.feature.reader.NoteDraft
import io.github.martindavinci.lunaris.pdf.feature.reader.SaveSheet
import io.github.martindavinci.lunaris.pdf.ui.theme.LunarisPdfTheme

@Preview
@Composable
private fun AnnotateBarsPreview() {
    LunarisPdfTheme {
        Column {
            AnnotateTopBar(DocumentEdits(changes = 2, pages = 1, canUndo = true), onAction = {})
            AnnotateToolBar(AnnotationUiState(tool = AnnotationTool.PEN), onAction = {})
        }
    }
}

@Preview
@Composable
private fun SaveAnnotationsPreview() {
    LunarisPdfTheme {
        Box(Modifier.fillMaxSize()) {
            SaveAnnotationsSheet(SaveSheet(5, 2, signed = true, leaveAfter = false), canWrite = false, onAction = {}, onSaveCopy = {})
        }
    }
}

@Preview
@Composable
private fun NotePreview() {
    LunarisPdfTheme { NoteSheet(NoteDraft("n", "See page 3"), onAction = {}) }
}

@Preview
@Composable
private fun SignaturesPreview() {
    LunarisPdfTheme { SignaturesPanel(listOf(SavedSignature("a", "")), onAction = {}) }
}

@Preview
@Composable
private fun SignaturePadPreview() {
    LunarisPdfTheme { SignaturePad(onDone = {}, onCancel = {}) }
}
