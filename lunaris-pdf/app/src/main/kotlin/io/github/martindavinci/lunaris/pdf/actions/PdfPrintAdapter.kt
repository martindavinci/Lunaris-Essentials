package io.github.martindavinci.lunaris.pdf.actions

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Hands the file itself to the printing service, which picks the pages it needs. */
class PdfPrintAdapter(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val name: String,
    private val pages: Int,
) : PrintDocumentAdapter() {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pages)
            .build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        val job = scope.launch {
            val copied = try {
                withContext(Dispatchers.IO) {
                    val input = resolver.openInputStream(uri) ?: throw IOException("cannot read the document")
                    input.use {
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            val buffer = ByteArray(BUFFER_BYTES)
                            while (true) {
                                ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                }
                true
            } catch (_: IOException) {
                false
            } catch (_: SecurityException) {
                false
            }
            if (copied) callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) else callback.onWriteFailed(null)
        }
        cancellationSignal?.setOnCancelListener {
            job.cancel()
            callback.onWriteCancelled()
        }
    }

    override fun onFinish() {
        scope.cancel()
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
    }
}
