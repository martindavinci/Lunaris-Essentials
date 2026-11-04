package io.github.martindavinci.lunaris.pdf.feature.pages

import io.github.martindavinci.lunaris.pdf.feature.work.JobKind
import io.github.martindavinci.lunaris.pdf.feature.work.JobPlan
import io.github.martindavinci.lunaris.pdf.feature.work.JobSession
import io.github.martindavinci.lunaris.pdf.feature.work.JobTarget
import io.github.martindavinci.lunaris.pdf.feature.work.WorkState
import io.github.martindavinci.lunaris.pdf.pages.OutputFiles
import io.github.martindavinci.lunaris.pdf.pages.PageJob
import io.github.martindavinci.lunaris.pdf.pages.PageSlot
import io.github.martindavinci.lunaris.pdf.pages.SplitPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The three ways the pages become new files. */
internal class PagesJobs(
    private val scope: CoroutineScope,
    private val session: JobSession,
    private val files: OutputFiles,
    private val sources: PageSources,
    private val ui: () -> PagesUiState,
    private val update: ((PagesUiState) -> PagesUiState) -> Unit,
) {
    fun save(uri: String, name: String) {
        closeFailed()
        update { it.copy(askingToLeave = false) }
        run(JobKind.SAVE, listOf(JobTarget(uri, name)), listOf(ui().slots))
    }

    fun extract(uri: String, name: String) {
        closeFailed()
        val state = ui()
        run(JobKind.EXTRACT, listOf(JobTarget(uri, name)), listOf(state.slots.filter { it.id in state.selected }))
    }

    fun splitting(change: (SplitPlan) -> SplitPlan) {
        val panel = ui().panel as? PagesPanel.Split ?: return
        update { it.copy(panel = PagesPanel.Split(change(panel.plan))) }
    }

    fun split(folder: String, documentName: String) {
        val state = ui()
        val plan = (state.panel as? PagesPanel.Split)?.plan ?: return
        val outputs = plan.outputs(state.slots) ?: return
        update { it.copy(panel = null) }
        scope.launch {
            val targets = mutableListOf<JobTarget>()
            for (name in plan.fileNames(documentName, state.slots.size)) {
                val made = files.createIn(folder, name)
                if (made == null) {
                    targets.forEach { files.delete(it.uri) }
                    update { it.copy(notice = PagesNotice.FOLDER_FAILED) }
                    return@launch
                }
                targets += JobTarget(made, name)
            }
            run(JobKind.SPLIT, targets, outputs)
        }
    }

    /** The one left open by "save somewhere else" is forgotten, and so is its target. */
    private fun closeFailed() {
        if (ui().work is WorkState.Failed) session.closeFailure()
    }

    private fun run(kind: JobKind, targets: List<JobTarget>, outputs: List<List<PageSlot>>) {
        val plan = JobPlan(PageJob(sources.kinds, outputs), sources.uris)
        session.start(kind, targets) { plan }
    }
}
