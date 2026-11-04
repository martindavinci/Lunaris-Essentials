package io.github.martindavinci.lunaris.pdf.pages

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.martindavinci.lunaris.pdf.annotate.EditedDocuments
import io.github.martindavinci.lunaris.pdf.annotate.EditedFileStore
import java.io.File
import javax.inject.Inject

/** The new file while it comes out of the page, in a cache folder of its own. */
class WorkFiles private constructor(private val context: Context, store: EditedFileStore) : EditedDocuments by store {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context, EditedFileStore(File(context.cacheDir, FOLDER)))

    fun freeBytes(): Long = StatFs(context.cacheDir.path).availableBytes

    private companion object {
        const val FOLDER = "work"
    }
}
