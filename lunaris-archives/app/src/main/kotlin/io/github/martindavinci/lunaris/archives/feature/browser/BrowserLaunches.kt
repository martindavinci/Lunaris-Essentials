package io.github.martindavinci.lunaris.archives.feature.browser

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.martindavinci.lunaris.archives.R

/** Starts what the browser asked for; false when no app takes it. */
internal fun Context.launch(request: LaunchRequest): Boolean {
    val intent = when (request) {
        is LaunchRequest.View -> viewIntent(request.uri, request.mime)
        is LaunchRequest.Chooser -> Intent.createChooser(viewIntent(request.uri, request.mime), null)
        is LaunchRequest.Send -> Intent.createChooser(sendIntent(request.uris, request.mime), null)
        is LaunchRequest.Extract, is LaunchRequest.Nested -> return true
    }
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private fun viewIntent(uri: String, mime: String) = Intent(Intent.ACTION_VIEW)
    .setDataAndType(Uri.parse(uri), mime)
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

private fun sendIntent(uris: List<String>, mime: String): Intent {
    val parsed = uris.map(Uri::parse)
    val intent = if (parsed.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, parsed.single())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(parsed))
    }
    val clip = ClipData.newRawUri(null, parsed.first())
    parsed.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
    intent.setType(mime)
    intent.clipData = clip
    return intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

/** The text of a notice, and its argument when it has one. */
internal fun BrowserNotice.message(): Pair<Int, String?> = when (this) {
    is BrowserNotice.Damaged -> R.string.notice_damaged to name
    BrowserNotice.TooDeep -> R.string.notice_too_deep to null
    BrowserNotice.NoSpace -> R.string.notice_no_space to null
    BrowserNotice.Changed -> R.string.notice_changed to null
    BrowserNotice.ShareFailed -> R.string.notice_share_failed to null
}
