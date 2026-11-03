package io.github.martindavinci.lunaris.pdf.feature.reader

import io.github.martindavinci.lunaris.pdf.viewer.ViewerChannel
import io.github.martindavinci.lunaris.pdf.viewer.ViewerCommand

/** Long moves through the document, and the way back from them. */
internal class ReaderJumps(
    private val viewer: ViewerChannel,
    private val state: () -> ReaderUiState,
    private val update: ((ReaderUiState) -> ReaderUiState) -> Unit,
) {
    /** A jump just asked for: pages reported until it lands still belong to where it started. */
    private var landing = false

    fun goTo(page: Int) {
        moved(state().page, page)
        viewer.send(ViewerCommand.GoToPage(page))
    }

    fun moved(from: Int, to: Int) {
        val jump = PageJump.after(state().jump, from, to)
        if (jump != state().jump) landing = true
        update { it.copy(jump = jump) }
    }

    fun goBack() {
        val jump = state().jump ?: return
        landing = true
        update { it.copy(jump = jump.reversed()) }
        viewer.send(ViewerCommand.GoToPage(jump.returnTo))
    }

    fun whileReading(jump: PageJump?, page: Int): PageJump? {
        if (jump == null || !landing) return jump?.whileReading(page)
        if (PageJump.isNear(page, jump.landedOn)) landing = false
        return jump
    }
}
