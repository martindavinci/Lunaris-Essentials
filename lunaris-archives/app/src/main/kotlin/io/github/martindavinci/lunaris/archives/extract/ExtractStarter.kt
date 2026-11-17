package io.github.martindavinci.lunaris.archives.extract

import io.github.martindavinci.lunaris.archives.session.ArchiveSession
import javax.inject.Inject
import javax.inject.Singleton

/** Runs the last extraction again into another folder (after "Choose" when the folder went away). */
fun interface ExtractAgain {
    fun again(target: SafTarget)
}

/** Turns "these entries of that open archive, into this folder" into a job for the runner. */
@Singleton
class ExtractStarter @Inject constructor(
    private val session: ArchiveSession,
    private val runner: ExtractionRunner,
) : ExtractAgain {
    private var lastArchiveId: String? = null
    private var lastIndices: LongArray = LongArray(0)

    /** False when the archive is no longer open. */
    fun start(archiveId: String, indices: LongArray, target: SafTarget): Boolean {
        val archive = session.get(archiveId) ?: return false
        lastArchiveId = archiveId
        lastIndices = indices
        val all = archive.index.entries
        // "Extract all" needs no lookup; a selection is one pass with a set.
        val entries = if (indices.size == all.size) all else indices.toHashSet().let { wanted -> all.filter { it.index in wanted } }
        runner.reset()
        runner.start(ExtractJob(archive, entries, session.passwordFor(archiveId), target, onWrongPassword = { session.forgetPassword(archiveId) }))
        return true
    }

    override fun again(target: SafTarget) {
        val id = lastArchiveId ?: return
        start(id, lastIndices, target)
    }
}
