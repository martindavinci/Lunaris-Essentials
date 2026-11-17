package io.github.martindavinci.lunaris.archives.create

import io.github.martindavinci.lunaris.archives.extract.FreeSpace
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One new archive at a time, written into the file chosen with "Save as"; an unfinished one is deleted. */
@Singleton
class CreateRunner internal constructor(private val io: CreateIo, private val writers: WriterFactory, dispatcher: CoroutineDispatcher) {
    @Inject constructor(io: CreateIo, writers: ArchiveWriters) : this(io, writers, Dispatchers.IO)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow<CreateState>(CreateState.Idle)
    val state: StateFlow<CreateState> = _state.asStateFlow()
    private var job: Job? = null

    fun start(
        files: List<SourceFile>, target: String, name: String, format: ArchiveFormat, level: CompressionLevel, password: CharArray?,
        skipped: List<String> = emptyList(),
    ) {
        if (_state.value is CreateState.Running) return
        val total = files.sumOf { it.sizeBytes ?: 0 }
        _state.value = CreateState.Running(0, files.size, files.firstOrNull()?.pathInZip.orEmpty(), 0, total)
        job = scope.launch {
            var written = 0L
            try {
                val out = try {
                    io.openOutput(target)
                } catch (_: Exception) {   // IOException, or the provider's own SecurityException / IllegalArgumentException
                    throw TargetGone()
                }
                val writer = try {
                    writers.open(format, level, password.takeIf { format.takesPassword }, out, total) { copied, all ->
                        ensureActive()
                        val done = if (all > 0) total * copied / all else total
                        _state.value = CreateState.Running(files.size, files.size, name, done, total)
                    }
                } catch (e: Exception) {
                    out.close()
                    throw e
                }
                writer.use { zip ->
                    try {
                        addAll(zip, files, total) { written = it }
                    } catch (e: Throwable) {
                        zip.abort()
                        throw e
                    }
                }
                _state.value = CreateState.Finished(name, io.size(target) ?: written, io.location(target), target, skipped)
            } catch (_: CancellationException) {
                runCatching { io.delete(target) }
                _state.value = CreateState.Idle
            } catch (_: TargetGone) {
                _state.value = CreateState.Failed(CreateFailure.TARGET_GONE)
            } catch (_: SourceReadException) {
                fail(target, CreateFailure.SOURCE_GONE)
            } catch (e: IOException) {
                fail(target, if (FreeSpace.isNoSpace(e)) CreateFailure.NO_SPACE else CreateFailure.TARGET_GONE)
            } catch (_: RuntimeException) {   // zip4j or a provider, somewhere nobody foresaw: a failed run, not a crash
                fail(target, CreateFailure.TARGET_GONE)
            } finally {
                password?.fill(WIPED)
            }
        }
    }

    private fun CoroutineScope.addAll(writer: ArchiveWriter, files: List<SourceFile>, total: Long, onWritten: (Long) -> Unit) {
        var written = 0L
        files.forEachIndexed { i, file ->
            _state.value = CreateState.Running(i, files.size, file.pathInZip, written, total)
            val input = try {
                io.openInput(file.uri)
            } catch (e: IOException) {
                throw SourceReadException(e)
            } catch (e: RuntimeException) {   // a share's permission that has ended, or the provider's own exception
                throw SourceReadException(IOException(e))
            }
            val before = written
            writer.add(file.pathInZip, file.sizeBytes, file.modifiedMillis, input) { bytes ->
                ensureActive()
                written = before + bytes
                onWritten(written)
                _state.value = CreateState.Running(i, files.size, file.pathInZip, written, total)
            }
        }
    }

    /** Removes a document that will not be written after all. */
    fun discard(target: String) {
        runCatching { io.delete(target) }
    }

    fun stop() {
        job?.cancel()
    }

    fun reset() {
        if (_state.value !is CreateState.Running) _state.value = CreateState.Idle
    }

    private fun fail(target: String, reason: CreateFailure) {
        runCatching { io.delete(target) }
        _state.value = CreateState.Failed(reason)
    }

    private class TargetGone : IOException("target gone")

    private companion object { val WIPED = Char(0) }
}
