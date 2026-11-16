package io.github.martindavinci.lunaris.archives.files

import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.browse.EntryMime

/** Copies of entries for other apps, read-only: 0.1 never takes writes back. */
class EntryFileProvider : FileProvider(R.xml.file_paths) {
    override fun getType(uri: Uri): String =
        EntryMime.of(uri.lastPathSegment.orEmpty(), null) { ext -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != READ_ONLY) throw SecurityException("read-only")
        return super.openFile(uri, mode)
    }

    private companion object { const val READ_ONLY = "r" }
}
