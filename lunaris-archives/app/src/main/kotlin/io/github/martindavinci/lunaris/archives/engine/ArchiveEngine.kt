package io.github.martindavinci.lunaris.archives.engine

import io.github.martindavinci.lunaris.archives.archive.RawPath
import io.github.martindavinci.lunaris.archives.session.ArchiveSource
import java.io.IOException
import kotlinx.coroutines.flow.Flow

/** Main process: the only door to the engines. Easy to replace with a fake in tests. */
interface ArchiveEngine {
    /**
     * [onEntries] is told now and then how many entries have been read so far, for "Reading the
     * archive… N items". It may be called on any thread.
     */
    suspend fun list(source: ArchiveSource, route: EngineRoute, password: CharArray?, onEntries: (Int) -> Unit = {}): ListResult

    /** Cold: collecting starts one engine pass over the archive; cancelling the collection stops the engine. */
    fun extract(
        source: ArchiveSource, route: EngineRoute, password: CharArray?, indices: LongArray,
        expected: Map<Long, RawPath>,
    ): Flow<EngineEvent>
}

sealed interface EngineEvent {
    data class EntryStarted(val index: Long) : EngineEvent
    class Bytes(val chunk: ByteArray, val length: Int) : EngineEvent
    data class Progress(val archiveBytesRead: Long) : EngineEvent
    data class EntryFinished(val index: Long, val outcome: EntryOutcome) : EngineEvent
    data class Finished(val status: EngineStatus) : EngineEvent
}

class EngineStoppedException : IOException("engine stopped")
