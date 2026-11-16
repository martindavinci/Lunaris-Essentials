package io.github.martindavinci.lunaris.archives.create

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class CreateRunnerTest {
    private val dispatcher = StandardTestDispatcher()
    private val io = object : CreateIo {
        val sources = mutableMapOf("s:a" to ByteArray(100) { 1 }, "s:b" to ByteArray(200) { 2 })
        val targets = mutableMapOf<String, ByteArrayOutputStream>()
        var targetGone = false
        var noSpace = false
        var onRead: (() -> Unit)? = null
        override fun openInput(uri: String): InputStream {
            onRead?.invoke()
            return sources[uri]?.inputStream() ?: throw IOException("gone")
        }
        override fun openOutput(uri: String): OutputStream {
            if (targetGone) throw IOException("target gone")
            return object : ByteArrayOutputStream() {
                override fun write(b: ByteArray, off: Int, len: Int) {
                    if (noSpace) throw IOException("write failed: ENOSPC")
                    super.write(b, off, len)
                }
            }.also { targets[uri] = it }
        }
        override fun delete(uri: String) { targets.remove(uri) }
        override fun size(uri: String): Long? = targets[uri]?.size()?.toLong()
        override fun location(uri: String): String? = "Download"
    }
    private val runner = CreateRunner(io, dispatcher)
    private val files = listOf(SourceFile("s:a", "a.bin", 100, 0), SourceFile("s:b", "dir/b.bin", 200, 0))
    private fun start(password: CharArray? = null, list: List<SourceFile> = files) =
        runner.start(list, "t:1", "Holiday.zip", CompressionLevel.NORMAL, password, skipped = listOf("Doc"))

    @Test fun streamsEveryFile_andFinishesWithSizeAndLocation() = runTest(dispatcher) {
        start(); advanceUntilIdle()
        val done = runner.state.value as CreateState.Finished
        assertThat(done.name).isEqualTo("Holiday.zip"); assertThat(done.location).isEqualTo("Download")
        assertThat(done.sizeBytes).isEqualTo(io.targets.getValue("t:1").size().toLong()); assertThat(done.skipped).containsExactly("Doc")
    }
    @Test fun progressCountsFiles() = runTest(dispatcher) {
        val seen = mutableListOf<CreateState>()
        io.onRead = { seen += runner.state.value }
        start(); advanceUntilIdle()
        assertThat(seen.map { (it as CreateState.Running).added }).containsExactly(0, 1).inOrder()
    }
    @Test fun stop_deletesTheUnfinishedTarget() = runTest(dispatcher) {
        io.onRead = { runner.stop() }
        start(); advanceUntilIdle()
        assertThat(io.targets).isEmpty(); assertThat(runner.state.value).isEqualTo(CreateState.Idle)
    }
    @Test fun sourceGoneMidway_failsWithSourceGone_andDeletesTheTarget() = runTest(dispatcher) {
        start(list = files + SourceFile("s:gone", "c.bin", 1, 0)); advanceUntilIdle()
        assertThat(runner.state.value).isEqualTo(CreateState.Failed(CreateFailure.SOURCE_GONE)); assertThat(io.targets).isEmpty()
    }
    @Test fun targetGone_failsWithTargetGone() = runTest(dispatcher) {
        io.targetGone = true
        start(); advanceUntilIdle()
        assertThat(runner.state.value).isEqualTo(CreateState.Failed(CreateFailure.TARGET_GONE))
    }
    @Test fun noSpace_failsWithNoSpace_andDeletesTheTarget() = runTest(dispatcher) {
        io.noSpace = true
        start(); advanceUntilIdle()
        assertThat(runner.state.value).isEqualTo(CreateState.Failed(CreateFailure.NO_SPACE)); assertThat(io.targets).isEmpty()
    }
    @Test fun passwordCopyIsWipedAtTheEnd() = runTest(dispatcher) {
        val password = "Secret-2026".toCharArray()
        start(password); runCurrent(); advanceUntilIdle()
        assertThat(password.all { it == Char(0) }).isTrue()
    }
}
