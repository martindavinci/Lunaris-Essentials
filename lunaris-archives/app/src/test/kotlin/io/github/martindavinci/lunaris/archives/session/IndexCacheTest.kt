package io.github.martindavinci.lunaris.archives.session

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.engine.EngineRoute
import io.github.martindavinci.lunaris.archives.safety.Limits
import io.github.martindavinci.lunaris.archives.testing.index
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class IndexCacheTest {
    @get:Rule val temp = TemporaryFolder()
    private val cache by lazy { IndexCache(temp.root) }
    private fun key(n: Int) = "%016x".format(n)
    private val idx = index("docs/a.md", "b.txt").copy(comment = "hello")

    @Test fun roundTrip() {
        cache.write(key(1), EngineRoute.LIB_RAR, idx)
        val (route, back) = cache.read(key(1))!!
        assertThat(route).isEqualTo(EngineRoute.LIB_RAR)
        assertThat(back.entries.map { it.displayPath }).containsExactly("docs/a.md", "b.txt").inOrder()
        assertThat(back.comment).isEqualTo("hello")
    }
    @Test fun encryptedNames_areNeverWritten() {
        cache.write(key(2), EngineRoute.SEVEN_Z, idx.copy(encryption = Encryption.HEADERS))
        assertThat(cache.read(key(2))).isNull()
    }
    @Test fun oldestGoBeyondTheLimit() {
        (0..Limits.INDEX_CACHE_FILES).forEach { n ->
            cache.write(key(n), EngineRoute.LIBARCHIVE, idx)
            temp.root.resolve("index/${key(n)}.idx").setLastModified(1_000_000L + n * 1000L)
        }
        cache.write(key(99), EngineRoute.LIBARCHIVE, idx)
        assertThat(cache.read(key(0))).isNull()
        assertThat(cache.read(key(99))).isNotNull()
    }
    @Test fun aDamagedFile_isNullAndDeleted() {
        cache.write(key(3), EngineRoute.LIBARCHIVE, idx)
        val file = temp.root.resolve("index/${key(3)}.idx")
        file.writeBytes(file.readBytes().copyOf(10))
        assertThat(cache.read(key(3))).isNull()
        assertThat(file.exists()).isFalse()
    }
    @Test fun removeAndClear() {
        cache.write(key(4), EngineRoute.LIBARCHIVE, idx); cache.write(key(5), EngineRoute.LIBARCHIVE, idx)
        cache.remove(key(4))
        assertThat(cache.read(key(4))).isNull()
        cache.clear()
        assertThat(cache.read(key(5))).isNull()
    }
    @Test fun foreignKeys_neverBecomeFileNames() {
        cache.write("../../x", EngineRoute.LIBARCHIVE, idx)
        assertThat(temp.root.walk().filter { it.isFile }.toList()).isEmpty()
    }
}
