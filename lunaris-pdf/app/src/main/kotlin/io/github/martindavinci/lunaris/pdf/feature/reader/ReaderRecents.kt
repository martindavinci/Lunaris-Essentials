package io.github.martindavinci.lunaris.pdf.feature.reader

import io.github.martindavinci.lunaris.pdf.recents.DocumentThumbnails
import io.github.martindavinci.lunaris.pdf.recents.RecentVisit
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the recents learn from the Reader. Only a file that really opened joins them, and the first
 * page of a file with a password is never kept.
 */
internal class ReaderRecents(
    private val scope: CoroutineScope,
    private val touch: suspend (RecentVisit) -> Unit,
    private val thumbnails: DocumentThumbnails,
    private val askForThumbnail: () -> Unit,
) {
    private var joined = false

    fun onLoaded(visit: RecentVisit) {
        if (joined) return
        joined = true
        scope.launch {
            touch(visit)
            if (!visit.locked && !thumbnails.has(visit.uri)) askForThumbnail()
        }
    }

    fun onThumbnail(uri: String?, base64: String) {
        uri ?: return
        val jpeg = try {
            Base64.getDecoder().decode(base64)
        } catch (_: IllegalArgumentException) {
            return
        }
        scope.launch { thumbnails.save(uri, jpeg) }
    }
}
