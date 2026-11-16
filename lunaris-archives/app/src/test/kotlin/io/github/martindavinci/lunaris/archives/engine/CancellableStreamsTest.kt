package io.github.martindavinci.lunaris.archives.engine

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class CancellableStreamsTest {
    private fun file(bytes: ByteArray) = File.createTempFile("cancel", ".bin").apply { writeBytes(bytes); deleteOnExit() }

    @Test fun readsUntilTheJobIsCancelled() {
        val job = EngineJob(1)
        val input = CancellableStreams.input(ByteArray(10).inputStream(), job)
        assertThat(input.read()).isEqualTo(0)
        job.cancel()
        assertThrows(CancellationException::class.java) { input.read() }
    }
    @Test fun channelReadsAreCancellableToo() {
        val job = EngineJob(2)
        val ch = CancellableStreams.channel(FileChannel.open(file(ByteArray(10)).toPath()), job)
        assertThat(ch.read(ByteBuffer.allocate(4))).isEqualTo(4)
        job.cancel()
        assertThrows(CancellationException::class.java) { ch.read(ByteBuffer.allocate(4)) }
    }
    @Test fun channelInput_readsFromItsStartWithoutMovingTheChannel() {
        FileChannel.open(file(byteArrayOf(1, 2, 3, 4, 5)).toPath()).use { channel ->
            val input = ChannelInput(channel, 2, EngineJob(3))
            assertThat(input.read()).isEqualTo(3)
            assertThat(input.skip(1)).isEqualTo(1)
            assertThat(input.read()).isEqualTo(5)
            assertThat(input.read()).isEqualTo(-1)
            assertThat(input.skip(10)).isEqualTo(0)
            assertThat(channel.position()).isEqualTo(0)
        }
    }
    @Test fun channelInput_closeLeavesTheChannelOpen() {
        FileChannel.open(file(ByteArray(3)).toPath()).use { channel ->
            ChannelInput(channel, 0, EngineJob(4)).close()
            assertThat(channel.isOpen).isTrue()
        }
    }
    @Test fun channelInput_stopsWhenCancelled() {
        FileChannel.open(file(ByteArray(3)).toPath()).use { channel ->
            val job = EngineJob(5)
            val input = ChannelInput(channel, 0, job)
            job.cancel()
            assertThrows(CancellationException::class.java) { input.read(ByteArray(2), 0, 2) }
        }
    }
}
