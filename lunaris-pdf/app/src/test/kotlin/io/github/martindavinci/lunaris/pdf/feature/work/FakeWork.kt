package io.github.martindavinci.lunaris.pdf.feature.work

import io.github.martindavinci.lunaris.pdf.annotate.EditedDocuments
import io.github.martindavinci.lunaris.pdf.pages.OutputFiles
import io.github.martindavinci.lunaris.pdf.pages.OutputInfo
import io.github.martindavinci.lunaris.pdf.pages.WorkChannel
import io.github.martindavinci.lunaris.pdf.pages.WorkCommand
import io.github.martindavinci.lunaris.pdf.pages.WorkEvent
import kotlinx.coroutines.flow.MutableSharedFlow

internal class FakeWorkChannel : WorkChannel {
    val flow = MutableSharedFlow<WorkEvent>(extraBufferCapacity = 64)
    val sent = mutableListOf<WorkCommand>()
    override val events get() = flow
    override fun send(command: WorkCommand) {
        sent += command
    }
    override fun report(event: WorkEvent) {
        flow.tryEmit(event)
    }

    /** One output of one piece, written and saved. */
    fun makeOutput(output: Int) {
        report(WorkEvent.Chunk(output, 0, 1, "AQ=="))
        report(WorkEvent.OutputDone(output, 1))
    }
}

internal class FakeWorkFiles : EditedDocuments {
    override suspend fun begin() = Unit
    override suspend fun append(index: Int, bytes: ByteArray) = true
    override suspend fun finish(size: Long): String? = "file://made"
    override suspend fun clear() = Unit
}

internal class FakeOutputFiles(var saves: Boolean = true) : OutputFiles {
    val savedTo = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    val remembered = mutableListOf<Pair<String, Int>>()
    val created = mutableListOf<String>()
    var withContent = emptySet<String>()
    var sizes = emptyMap<String, Long>()
    var refuseToCreate = emptySet<String>()

    override suspend fun save(from: String, to: String): Boolean {
        if (saves) savedTo += to
        return saves
    }
    override suspend fun isEmpty(uri: String) = uri !in withContent
    override suspend fun delete(uri: String) {
        deleted += uri
    }
    override suspend fun createIn(folder: String, name: String): String? {
        if (name in refuseToCreate) return null
        created += name
        return "$folder/$name"
    }
    override suspend fun describe(uri: String) = OutputInfo(uri.substringAfterLast('/'), "Download", sizes[uri] ?: 2048)
    override suspend fun remember(uri: String, name: String, pages: Int) {
        remembered += uri to pages
    }
}
