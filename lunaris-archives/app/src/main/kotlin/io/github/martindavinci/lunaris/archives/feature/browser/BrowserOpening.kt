package io.github.martindavinci.lunaris.archives.feature.browser

import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.browse.EntryKind
import io.github.martindavinci.lunaris.archives.browse.EntryKinds
import io.github.martindavinci.lunaris.archives.browse.FolderRow
import io.github.martindavinci.lunaris.archives.files.CopyResult
import io.github.martindavinci.lunaris.archives.files.EntryOpener
import io.github.martindavinci.lunaris.archives.safety.Limits
import io.github.martindavinci.lunaris.archives.session.ArchiveSession
import io.github.martindavinci.lunaris.archives.session.OpenArchive
import io.github.martindavinci.lunaris.archives.session.OpenRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A tap on a file: copy it, then hand it to another app (or open it here when it is an archive).
 * The opening bar shows only when the copy takes longer than [Limits.OPENING_BAR_DELAY_MILLIS].
 */
internal class BrowserOpening(
    private val scope: CoroutineScope,
    private val session: ArchiveSession,
    private val opener: EntryOpener,
    private val state: MutableStateFlow<BrowserUiState>,
    private val onChanged: () -> Unit,
) {
    private var job: Job? = null
    private var launchedRow: FolderRow? = null

    /** The row that waits for a password (B6 asks for it). */
    var waiting: FolderRow? = null
        private set

    fun open(archive: OpenArchive, row: FolderRow, chooser: Boolean) {
        val entry = row.entry ?: return
        val nested = EntryKinds.of(row.name, EntryType.FILE) == EntryKind.ARCHIVE && !chooser
        if (nested && session.depth(archive.id) >= Limits.MAX_NESTING) {
            state.update { it.copy(notice = BrowserNotice.TooDeep) }
            return
        }
        job?.cancel()
        job = scope.launch {
            val bar = launch {
                delay(Limits.OPENING_BAR_DELAY_MILLIS)
                state.update { it.copy(opening = OpeningState(row.name, INDETERMINATE)) }
            }
            val result = opener.copy(archive, entry, session.passwordFor(archive.id)) { bytes ->
                val fraction = entry.size?.takeIf { it > 0 }?.let { (bytes.toFloat() / it).coerceIn(0f, 1f) } ?: INDETERMINATE
                state.update { s -> s.opening?.let { s.copy(opening = it.copy(fraction = fraction)) } ?: s }
            }
            bar.cancel()
            state.update { it.copy(opening = null) }
            handle(archive, row, result, nested, chooser)
        }
    }

    fun cancel() {
        job?.cancel()
        state.update { it.copy(opening = null) }
    }

    fun launchHandled(ok: Boolean) {
        val row = launchedRow
        launchedRow = null
        state.update { it.copy(launch = null, sheet = if (!ok && row != null) BrowserSheet.NoApp(row) else it.sheet) }
    }

    private fun handle(archive: OpenArchive, row: FolderRow, result: CopyResult, nested: Boolean, chooser: Boolean) {
        val launch: LaunchRequest? = when (result) {
            is CopyResult.Ready -> when {
                nested -> LaunchRequest.Nested(
                    session.register(OpenRequest(result.uri, archive.id, row.entry?.index, result.path)),
                )
                chooser -> LaunchRequest.Chooser(result.uri, result.mime)
                else -> LaunchRequest.View(result.uri, result.mime)
            }
            CopyResult.NeedsPassword, CopyResult.WrongPassword -> {
                waiting = row
                val wrong = result == CopyResult.WrongPassword
                state.update { it.copy(password = PasswordPrompt(row.name, PasswordTarget.ENTRY, wrong)) }
                null
            }
            CopyResult.Damaged, CopyResult.Unsupported -> notice(BrowserNotice.Damaged(row.name))
            CopyResult.NoSpace -> notice(BrowserNotice.NoSpace)
            CopyResult.Rar4Password -> null.also { state.update { it.copy(sheet = BrowserSheet.Rar4) } }
            CopyResult.Changed -> null.also { onChanged() }
            CopyResult.Cancelled -> null
        }
        if (launch != null) {
            launchedRow = row
            state.update { it.copy(launch = launch) }
        }
    }

    private fun notice(notice: BrowserNotice): LaunchRequest? {
        state.update { it.copy(notice = notice) }
        return null
    }

    private companion object { const val INDETERMINATE = -1f }
}
