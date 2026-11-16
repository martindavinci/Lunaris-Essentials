package io.github.martindavinci.lunaris.archives.testing

import io.github.martindavinci.lunaris.archives.archive.RawPath
import io.github.martindavinci.lunaris.archives.engine.ArchiveEngine
import io.github.martindavinci.lunaris.archives.engine.EngineEvent
import io.github.martindavinci.lunaris.archives.engine.EngineRoute
import io.github.martindavinci.lunaris.archives.engine.ListResult
import io.github.martindavinci.lunaris.archives.session.ArchiveSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/** Answers from queues the test fills; records every call (passwords as text, for asserts). */
class FakeArchiveEngine : ArchiveEngine {
    val lists = mutableMapOf<EngineRoute, ArrayDeque<ListResult>>()
    val extracts = mutableMapOf<EngineRoute, List<EngineEvent>>()
    val listCalls = mutableListOf<Pair<EngineRoute, String?>>()
    val extractCalls = mutableListOf<Triple<EngineRoute, String?, List<Long>>>()
    /** Thrown by the next list calls, before any answer is taken. */
    val listFailures = ArrayDeque<Exception>()
    /** Thrown by extract calls: the archive went away or its permission did. */
    var extractFailure: Exception? = null
    /** When set, every list call waits for it: a slow archive. */
    var listGate: CompletableDeferred<Unit>? = null

    override suspend fun list(source: ArchiveSource, route: EngineRoute, password: CharArray?, onEntries: (Int) -> Unit): ListResult {
        listCalls += route to password?.concatToString()
        listGate?.await()
        listFailures.removeFirstOrNull()?.let { throw it }
        return lists[route]?.removeFirstOrNull() ?: error("no list answer for $route")
    }

    override fun extract(
        source: ArchiveSource, route: EngineRoute, password: CharArray?, indices: LongArray, expected: Map<Long, RawPath>,
    ): Flow<EngineEvent> {
        extractCalls += Triple(route, password?.concatToString(), indices.toList())
        extractFailure?.let { throw it }
        val wanted = indices.toSet()
        var current: Long? = null
        // Like a real engine: only the entries that were asked for come out.
        return (extracts[route] ?: error("no extract answer for $route")).filter { event ->
            if (event is EngineEvent.EntryStarted) current = event.index
            val keep = current == null || current in wanted
            if (event is EngineEvent.EntryFinished) current = null
            keep
        }.asFlow()
    }
}
