package io.github.martindavinci.lunaris.pdf.actions

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DocumentCopier @Inject constructor(
    @ApplicationContext private val context: Context,
) : DocumentActions {

    override suspend fun saveCopy(from: String, to: String): Boolean = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val source = from.toUri()
        val target = to.toUri()
        SafeCopy.save(
            temp = File(File(context.cacheDir, SAVE_FOLDER), UUID.randomUUID().toString()),
            openSource = { resolver.openInputStream(source) ?: throw FileNotFoundException(from) },
            openTarget = { resolver.openOutputStream(target, TRUNCATE) ?: throw FileNotFoundException(to) },
            targetHadContent = hasContent(target),
            deleteTarget = { DocumentsContract.deleteDocument(resolver, target) },
        )
    }

    override suspend fun prepareShare(from: String, name: String): String? = withContext(Dispatchers.IO) {
        SharedPdfFiles.removeOld(context.cacheDir, System.currentTimeMillis())
        val file = SharedPdfFiles.target(context.cacheDir, name, UUID.randomUUID().toString())
        val copied = attempt {
            checkNotNull(file.parentFile).mkdirs()
            context.contentResolver.openInputStream(from.toUri())?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            } ?: throw FileNotFoundException(from)
        }
        if (copied) FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString() else null
    }

    /** A size it does not know is taken as content: better an empty file left over than one lost. */
    private fun hasContent(uri: Uri): Boolean {
        var known = true
        val empty = attempt {
            context.contentResolver.openAssetFileDescriptor(uri, READ_ONLY)?.use { known = it.length != 0L }
        } && !known
        return !empty
    }

    private companion object {
        const val TRUNCATE = "wt"
        const val READ_ONLY = "r"
        const val SAVE_FOLDER = "save"
    }
}
