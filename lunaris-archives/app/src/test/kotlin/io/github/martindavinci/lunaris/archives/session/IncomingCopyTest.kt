package io.github.martindavinci.lunaris.archives.session

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.safety.Limits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.InputStream

class IncomingCopyTest {
    @get:Rule val temp = TemporaryFolder()
    private val copy by lazy { IncomingCopy(temp.root) }

    @Test fun aCopyWithNoSizeStops_beforeTheDiskIsFull() = runTest {
        val endless = object : InputStream() {
            override fun read() = 0
            override fun read(b: ByteArray, off: Int, len: Int) = len
        }
        val target = copy.target("k5", "a.zip")
        val failure = runCatching {
            copy.copy(endless, target, freeBytes = { Limits.CACHE_MARGIN_BYTES - 1 }, checkEvery = Limits.WIRE_CHUNK_BYTES.toLong()) {}
        }.exceptionOrNull()
        assertThat(failure).isInstanceOf(NoSpaceException::class.java)
        assertThat(target.exists()).isFalse()
    }
    @Test fun copiesEverythingAndReportsProgress() = runTest {
        val data = ByteArray(Limits.WIRE_CHUNK_BYTES * 3 + 5) { it.toByte() }
        val reports = mutableListOf<Long>()
        val file = copy.copy(data.inputStream(), copy.target("k1", "a.zip")) { reports += it }
        assertThat(file.readBytes()).isEqualTo(data)
        assertThat(reports.size).isAtLeast(4)
        assertThat(reports.last()).isEqualTo(data.size.toLong())
    }

    @Test fun cancellation_deletesThePartialFile() = runTest {
        val target = copy.target("k2", "a.zip")
        lateinit var job: Job
        val endless = object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int { job.cancel(); return len }
        }
        val running = async { copy.copy(endless, target) {} }
        job = running
        val failure = runCatching { running.await() }.exceptionOrNull()
        assertThat(failure).isInstanceOf(CancellationException::class.java)
        assertThat(target.exists()).isFalse()
    }

    @Test fun target_staysInsideItsFolder() {
        val target = copy.target("k3", "../../evil.zip")
        assertThat(target.name).isEqualTo("evil.zip")
        assertThat(target.parentFile).isEqualTo(temp.root.resolve("incoming").resolve("k3"))
    }

    @Test fun forget_deletesTheFolder() = runTest {
        val target = copy.copy(ByteArray(3).inputStream(), copy.target("k4", "a.zip")) {}
        copy.forget("k4")
        assertThat(target.parentFile!!.exists()).isFalse()
    }
}
