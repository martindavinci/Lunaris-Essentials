package io.github.martindavinci.lunaris.archives.feature.home

import io.github.martindavinci.lunaris.archives.feature.edits.EditsHost
import io.github.martindavinci.lunaris.archives.ui.launchOr
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.browse.EntryMime
import io.github.martindavinci.lunaris.archives.recents.RecentArchive
import kotlinx.coroutines.launch

/** Home with the system file picker, sharing a recent archive, the snackbars and the moves to other screens. */
@Composable
fun HomeRoute(
    onBrowse: (String) -> Unit,
    onExtractAll: (String) -> Unit,
    onCreate: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val say = { text: Int -> scope.launch { snackbar.showSnackbar(context.getString(text)) }; Unit }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onAction(HomeAction.OpenPicked(it.toString())) }
    }

    LaunchedEffect(state.navigate) {
        val nav = state.navigate ?: return@LaunchedEffect
        viewModel.onAction(HomeAction.Navigated)
        when (nav) {
            is HomeNav.Browser -> onBrowse(nav.requestId)
            is HomeNav.ExtractAll -> onExtractAll(nav.requestId)
            HomeNav.Create -> onCreate()
            HomeNav.Settings -> onSettings()
        }
    }
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        viewModel.onAction(HomeAction.NoticeShown)
        if (notice is HomeNotice.Removed) {
            val result = snackbar.showSnackbar(
                context.getString(R.string.home_removed), context.getString(R.string.home_undo), duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.onAction(HomeAction.UndoRemove)
        } else {
            snackbar.showSnackbar(context.getString(noticeText(notice)))
        }
    }

    val edits = EditsHost(null, snackbar)
    HomeScreen(
        state = state,
        onAction = { action ->
            if (action is HomeAction.Share && !context.shareArchive(action.item)) say(R.string.notice_share_failed)
            viewModel.onAction(action)
        },
        onPickFile = {
            picker.launchOr(ARCHIVE_TYPES) { say(R.string.home_no_picker) }
        },
        onCreate = onCreate,
        onSettings = onSettings,
        snackbar = snackbar,
        cards = { edits.Cards() },
    )
}

private fun noticeText(notice: HomeNotice): Int = when (notice) {
    HomeNotice.Gone -> R.string.home_gone
    HomeNotice.NoPicker -> R.string.home_no_picker
    HomeNotice.ShareFailed -> R.string.notice_share_failed
    HomeNotice.ExtractionRecovered -> R.string.home_extract_recovered
    is HomeNotice.Removed -> R.string.home_removed
}

/** False when no app takes it. */
private fun Context.shareArchive(item: RecentArchive): Boolean {
    val type = EntryMime.of(item.name, null) { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
    val send = Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return runCatching { startActivity(Intent.createChooser(send, null)) }.isSuccess
}

/** The archive types of the picker; octet-stream because many providers report it for .7z and .zst. */
private val ARCHIVE_TYPES = arrayOf(
    "application/zip", "application/x-7z-compressed", "application/vnd.rar", "application/x-rar-compressed",
    "application/x-tar", "application/gzip", "application/x-bzip2", "application/x-xz", "application/zstd",
    "application/x-lz4", "application/x-iso9660-image", "application/x-cpio", "application/octet-stream",
)
