package io.github.martindavinci.lunaris.archives.feature.browser

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.engine.ArchiveProblem
import io.github.martindavinci.lunaris.archives.ui.ProblemState

/**
 * One calm screen per way an archive can fail to open (frame 09 and spec "Stati non disegnati").
 * No red, no technical message: what it is, and what the user can do about it.
 */
@Composable
internal fun BrowserProblem(problem: ArchiveProblem, state: BrowserUiState, onAction: (BrowserAction) -> Unit) {
    val close = stringResource(R.string.close) to { onAction(BrowserAction.Up) }
    when (problem) {
        ArchiveProblem.RAR4_PASSWORD -> ProblemState(
            icon = Icons.Rounded.Lock,
            title = stringResource(R.string.problem_rar4_title),
            body = stringResource(R.string.problem_rar4_body),
            primary = close,
        )
        ArchiveProblem.DAMAGED -> ProblemState(
            icon = Icons.Rounded.Error,
            title = stringResource(R.string.problem_damaged_title),
            body = stringResource(R.string.problem_damaged_body),
            primary = close,
            // Offered only when some entries were read before the archive broke off.
            secondary = if (state.partial) stringResource(R.string.problem_show_readable) to { onAction(BrowserAction.ShowPartial) } else null,
        )
        ArchiveProblem.NOT_ARCHIVE -> Plain(R.string.problem_not_archive_title, R.string.problem_not_archive_body, close)
        ArchiveProblem.UNSUPPORTED_FEATURE -> Plain(R.string.problem_unsupported_title, R.string.problem_unsupported_body, close)
        ArchiveProblem.SPLIT_ARCHIVE -> Plain(R.string.problem_split_title, R.string.problem_split_body, close)
        ArchiveProblem.READER_STOPPED -> Plain(R.string.problem_stopped_title, R.string.problem_stopped_body, close)
        ArchiveProblem.GONE -> ProblemState(
            icon = Icons.Rounded.Error,
            title = stringResource(R.string.problem_gone_title, state.title),
            body = stringResource(R.string.problem_gone_body),
            primary = close,
        )
    }
}

@Composable
private fun Plain(title: Int, body: Int, close: Pair<String, () -> Unit>) {
    ProblemState(icon = Icons.Rounded.Error, title = stringResource(title), body = stringResource(body), primary = close)
}
