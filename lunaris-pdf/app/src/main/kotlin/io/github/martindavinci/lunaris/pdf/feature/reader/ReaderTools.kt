package io.github.martindavinci.lunaris.pdf.feature.reader

import io.github.martindavinci.lunaris.pdf.annotate.AnnotationHints
import io.github.martindavinci.lunaris.pdf.annotate.AnnotationOptions
import io.github.martindavinci.lunaris.pdf.annotate.AnnotationPalette
import io.github.martindavinci.lunaris.pdf.annotate.AnnotationTool
import io.github.martindavinci.lunaris.pdf.viewer.ViewerChannel
import io.github.martindavinci.lunaris.pdf.viewer.ViewerCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The tool in hand and its options, which the page always gets told. */
internal class ReaderTools(
    private val scope: CoroutineScope,
    private val viewer: ViewerChannel,
    private val hints: AnnotationHints,
    private val marks: ReaderMarks,
    private val state: () -> ReaderUiState,
    private val update: ((ReaderUiState) -> ReaderUiState) -> Unit,
    private val changeAnnotation: ((AnnotationUiState) -> AnnotationUiState) -> Unit,
) {
    fun start() {
        if (state().loading) return
        update { it.copy(annotation = AnnotationUiState(), chromeVisible = true) }
        send()
    }

    fun choose(tool: AnnotationTool) {
        changeAnnotation { it.copy(tool = tool, placingSignature = false) }
        send()
        when (tool) {
            AnnotationTool.PEN -> offerScrollHint()
            AnnotationTool.SIGN -> marks.openSignatures()
            else -> Unit
        }
    }

    fun chooseColor(color: String) = changeOptions { options ->
        when (state().annotation?.tool) {
            AnnotationTool.HIGHLIGHT -> options.copy(highlightColor = color)
            AnnotationTool.PEN -> options.copy(penColor = color)
            AnnotationTool.TEXT -> options.copy(textColor = color)
            else -> options
        }
    }

    fun changeOptions(change: (AnnotationOptions) -> AnnotationOptions) {
        changeAnnotation { it.copy(options = change(it.options)) }
        send()
    }

    fun send() {
        val annotation = state().annotation
        viewer.send(ViewerCommand.SetTool(annotation?.let { AnnotationPalette.spec(it.tool, it.options) }))
    }

    private fun offerScrollHint() {
        scope.launch {
            if (hints.shown.first() < SCROLL_HINT_TIMES) {
                hints.countOne()
                changeAnnotation { it.copy(showScrollHint = true) }
            }
        }
    }

    private companion object {
        const val SCROLL_HINT_TIMES = 3
    }
}
