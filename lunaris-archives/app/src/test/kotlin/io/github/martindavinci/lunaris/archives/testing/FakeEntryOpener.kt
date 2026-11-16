package io.github.martindavinci.lunaris.archives.testing

import io.github.martindavinci.lunaris.archives.archive.ArchiveEntry
import io.github.martindavinci.lunaris.archives.files.CopyResult
import io.github.martindavinci.lunaris.archives.files.EntryOpener
import io.github.martindavinci.lunaris.archives.session.OpenArchive
import kotlinx.coroutines.CompletableDeferred

/** Answers copies from a queue; [gate] makes a copy slow, [progress] is reported before it ends. */
class FakeEntryOpener : EntryOpener {
    val results = ArrayDeque<CopyResult>()
    val calls = mutableListOf<Pair<Long, String?>>()
    var gate: CompletableDeferred<Unit>? = null
    var progress: Long? = null

    override suspend fun copy(archive: OpenArchive, entry: ArchiveEntry, password: CharArray?, onProgress: (Long) -> Unit): CopyResult {
        calls += entry.index to password?.concatToString()
        progress?.let(onProgress)
        gate?.await()
        return results.removeFirstOrNull() ?: error("no copy answer")
    }
}
