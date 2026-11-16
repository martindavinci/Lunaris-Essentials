package io.github.martindavinci.lunaris.archives.engine

import java.io.FilterInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel

/** Reads that stop with a CancellationException once the job is cancelled. */
object CancellableStreams {
    fun input(input: InputStream, job: EngineJob): InputStream = object : FilterInputStream(input) {
        override fun read(): Int { job.throwIfCancelled(); return super.read() }
        override fun read(b: ByteArray, off: Int, len: Int): Int { job.throwIfCancelled(); return super.read(b, off, len) }
        override fun skip(n: Long): Long { job.throwIfCancelled(); return super.skip(n) }
    }

    fun channel(channel: SeekableByteChannel, job: EngineJob): SeekableByteChannel =
        object : SeekableByteChannel by channel {
            override fun read(dst: ByteBuffer): Int { job.throwIfCancelled(); return channel.read(dst) }
            override fun position(newPosition: Long): SeekableByteChannel {
                job.throwIfCancelled()
                channel.position(newPosition)
                return this
            }
        }
}

/**
 * The archive from [start] on, as a stream for zip4j and lib-rar, in place of
 * `Channels.newInputStream(channel)`:
 * - both libraries close the stream they are given; closing this one leaves the channel open,
 *   so every entry of a multi-entry extraction can still be read;
 * - skipping moves the position instead of reading (that stream skips by reading on Android);
 * - reads are positional, so the channel's own position is never moved under another user.
 */
class ChannelInput(private val channel: FileChannel, start: Long, private val job: EngineJob) : InputStream() {
    /** The offset of the next byte this stream will read. */
    var position: Long = start
        private set
    private val one = ByteArray(1)

    override fun read(): Int = if (read(one, 0, 1) < 1) END else one[0].toInt() and BYTE_MASK

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        job.throwIfCancelled()
        if (len == 0) return 0
        val n = channel.read(ByteBuffer.wrap(b, off, len), position)
        if (n > 0) position += n
        return n
    }

    override fun skip(n: Long): Long {
        job.throwIfCancelled()
        val step = minOf(n, channel.size() - position).coerceAtLeast(0)
        position += step
        return step
    }

    override fun available(): Int = (channel.size() - position).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    /** The channel belongs to the job, not to the library that was handed this stream. */
    override fun close() = Unit

    private companion object {
        const val END = -1
        const val BYTE_MASK = 0xFF
    }
}
