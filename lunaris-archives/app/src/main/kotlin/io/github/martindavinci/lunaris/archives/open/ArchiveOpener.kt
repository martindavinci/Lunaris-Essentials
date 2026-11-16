package io.github.martindavinci.lunaris.archives.open

import android.content.ContentResolver
import android.net.Uri
import javax.inject.Inject

/** Whether a remembered archive can still be read (its file and the permission are still there). */
fun interface ReadCheck {
    fun canRead(uri: String): Boolean
}

class ArchiveOpener @Inject constructor(private val resolver: ContentResolver) : ReadCheck {
    override fun canRead(uri: String): Boolean = runCatching { resolver.openFileDescriptor(Uri.parse(uri), READ)?.close() != null }
        .getOrDefault(false)

    private companion object { const val READ = "r" }
}
