package io.github.martindavinci.lunaris.archives.engine

import io.github.martindavinci.lunaris.archives.archive.RawPath
import io.github.martindavinci.lunaris.archives.safety.Limits

/** Pure: what the native status code and the error kept by the sink mean together. */
internal object LibarchiveStatus {
    fun resolve(code: Int, sink: NativeSink): EngineStatus {
        val status = EngineStatus.of(code)
        if (status != EngineStatus.DAMAGED || (sink.lastErrno == 0 && sink.lastMessage == null)) return status
        val kind = LibarchiveCodes.kind(sink.format, sink.filters).first
        return EngineErrors.classify(kind, sink.lastErrno, sink.lastMessage)
    }
}

/** libarchive behind the JNI surface: plain archives of every format. */
class LibarchiveReader : IsolatedReader {
    override fun list(job: EngineJob, volume: EngineVolume, password: CharArray?, out: WireWriter): EngineStatus {
        val sink = NativeSink(out, expected = null)
        return withNativeJob(job) { handle ->
            val status = LibarchiveStatus.resolve(NativeEngine.list(handle, intArrayOf(volume.fd), Limits.MAX_ENTRIES, sink), sink)
            if (sink.closed) throw SinkClosedException()
            val (kind, innerTar) = LibarchiveCodes.kind(sink.format, sink.filters)
            // Only a ZIP has buffered entries; for every other format they are on the wire already.
            val enriched = ZipEnrichment.apply(kind, volume, sink.buffered)
            out.send {
                enriched.entries.forEach { entry(it) }
                archive(
                    WireArchive(
                        kind, innerTar, LibarchiveCodes.encryption(sink.encrypted), enriched.comment,
                        capped = status == EngineStatus.LIMIT_REACHED,
                        partial = status == EngineStatus.TRUNCATED || status == EngineStatus.DAMAGED,
                    ),
                )
            }
            status
        }
    }

    override fun extract(
        job: EngineJob, volume: EngineVolume, password: CharArray?, indices: LongArray,
        expected: Map<Long, RawPath>?, out: WireWriter,
    ): EngineStatus {
        val wanted = indices.distinct().sorted().toLongArray()   // C needs them strictly increasing
        val sink = NativeSink(out, expected)
        return withNativeJob(job) { handle ->
            val status = LibarchiveStatus.resolve(NativeEngine.extract(handle, intArrayOf(volume.fd), wanted, sink), sink)
            if (sink.closed) throw SinkClosedException()
            status
        }
    }

    /** The native job lives exactly as long as [block]; cancelling is safe before, during and after. */
    private inline fun withNativeJob(job: EngineJob, block: (Long) -> EngineStatus): EngineStatus {
        val handle = NativeEngine.newJob()
        if (handle == 0L) return EngineStatus.OUT_OF_MEMORY
        try {
            if (!job.attach { NativeEngine.cancel(handle) }) return EngineStatus.CANCELLED
            return block(handle)
        } finally {
            job.detach()
            NativeEngine.freeJob(handle)
        }
    }
}
