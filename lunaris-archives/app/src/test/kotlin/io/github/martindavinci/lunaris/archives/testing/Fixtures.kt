package io.github.martindavinci.lunaris.archives.testing

import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.RawPath
import io.github.martindavinci.lunaris.archives.engine.EngineEvent
import io.github.martindavinci.lunaris.archives.engine.EngineJob
import io.github.martindavinci.lunaris.archives.engine.EngineVolume
import io.github.martindavinci.lunaris.archives.engine.IsolatedReader
import io.github.martindavinci.lunaris.archives.engine.WireCollector
import io.github.martindavinci.lunaris.archives.engine.WireReader
import io.github.martindavinci.lunaris.archives.engine.WireWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.channels.FileChannel

/** Committed test archives in src/test/resources/fixtures (made by tools/fixtures/make_*.py/ps1). */
object Fixtures {
    private const val DIR = "src/test/resources/fixtures"
    fun file(name: String): File = File(DIR, name).also { require(it.isFile) { "missing fixture $name" } }
    fun bytes(name: String): ByteArray = file(name).readBytes()

    /** Runs a reader's listing the way the engine service does and reads it back as the main process does. */
    fun runList(reader: IsolatedReader, name: String, password: String? = null, job: EngineJob = EngineJob(1)): WireCollector.Collected =
        runList(reader, file(name), password, job)

    fun runList(reader: IsolatedReader, archive: File, password: String? = null, job: EngineJob = EngineJob(1)): WireCollector.Collected {
        val bytes = run(archive) { volume, out -> reader.list(job, volume, password?.toCharArray(), out) }
        return WireCollector.index(WireReader(bytes.inputStream()), ArchiveKind.UNKNOWN)
    }

    /** Extracts the entries of [expected] (index to raw path, as the index has them) and reads the events back. */
    fun runExtract(
        reader: IsolatedReader, name: String, password: String?, expected: Map<Long, RawPath>, job: EngineJob = EngineJob(1),
    ): List<EngineEvent> = runExtract(reader, file(name), password, expected, job)

    fun runExtract(
        reader: IsolatedReader, archive: File, password: String?, expected: Map<Long, RawPath>, job: EngineJob = EngineJob(1),
    ): List<EngineEvent> {
        val bytes = run(archive) { volume, out -> reader.extract(job, volume, password?.toCharArray(), expected.keys.toLongArray(), null, out) }
        return WireCollector.events(WireReader(bytes.inputStream()), expected).toList()
    }

    /** The bytes of every extracted entry, in order. */
    fun contents(events: List<EngineEvent>): List<ByteArray> {
        val files = ArrayList<ByteArrayOutputStream>()
        events.forEach { event ->
            if (event is EngineEvent.EntryStarted) files += ByteArrayOutputStream()
            if (event is EngineEvent.Bytes) files.last().write(event.chunk, 0, event.length)
        }
        return files.map { it.toByteArray() }
    }

    /** A test archive of the vendored lib-rar, e.g. "rar723/encrypted-data.rar". */
    fun libRar(name: String): File =
        File("../lib-rar/src/test/resources/fixtures/$name").also { require(it.isFile) { "missing lib-rar fixture $name" } }

    private fun run(archive: File, work: (EngineVolume, WireWriter) -> io.github.martindavinci.lunaris.archives.engine.EngineStatus): ByteArray {
        val bytes = ByteArrayOutputStream()
        FileChannel.open(archive.toPath()).use { channel ->
            WireWriter(bytes).use { out -> out.status(work(EngineVolume(-1, channel), out)) }
        }
        return bytes.toByteArray()
    }
}
