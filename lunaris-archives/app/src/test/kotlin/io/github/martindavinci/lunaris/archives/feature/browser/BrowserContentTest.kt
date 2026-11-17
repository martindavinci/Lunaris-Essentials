package io.github.martindavinci.lunaris.archives.feature.browser

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.HostOs
import io.github.martindavinci.lunaris.archives.engine.EngineRoute
import io.github.martindavinci.lunaris.archives.session.ArchiveSource
import io.github.martindavinci.lunaris.archives.session.OpenArchive
import io.github.martindavinci.lunaris.archives.testing.index
import org.junit.Test

class BrowserContentTest {
    private fun archive(vararg paths: String) = OpenArchive(
        "id", ArchiveSource("content://a", "a.zip", 1, 1, "0123456789abcdef", index().kind) { error("unused") },
        EngineRoute.LIBARCHIVE, index(*paths), null, null,
    )

    @Test fun aFolder_bringsEverythingUnderIt_andNothingBeside() {
        val open = archive("a/", "a/x.txt", "a/b/y.txt", "ab/z.txt", "c.txt")
        assertThat(BrowserContent.indicesFor(open, listOf(4L), listOf(listOf("a"))).toList()).containsExactly(0L, 1L, 2L, 4L).inOrder()
    }

    /** Windows zippers write 0 (MS-DOS) too: the row would mislead, so it is left out. */
    @Test fun msdosHost_isNotShown_otherHostsAre() {
        val view = io.github.martindavinci.lunaris.archives.browse.FolderView(index("a.txt"))
        val dos = archive("a.txt").let { it.copy(index = it.index.copy(createdBy = HostOs.MSDOS)) }
        val unix = archive("a.txt").let { it.copy(index = it.index.copy(createdBy = HostOs.UNIX)) }
        assertThat(BrowserContent.header(dos, view).createdBy).isNull()
        assertThat(BrowserContent.header(unix, view).createdBy).isEqualTo(HostOs.UNIX)
    }

    /** A node_modules-like root: thousands of folders selected at once must not take minutes. */
    @Test fun manySelectedFolders_inABigArchive_areFast() {
        val folders = 4_000
        val open = archive(*Array(200_000) { i -> "d${i % folders}/f$i.txt" })
        val selected = (0 until folders step 2).map { listOf("d$it") }   // half the entries are under none of them
        val started = System.nanoTime()
        val indices = BrowserContent.indicesFor(open, emptyList(), selected)
        val millis = (System.nanoTime() - started) / NANOS_PER_MILLI
        assertThat(indices).hasLength(100_000)
        assertThat(millis).isLessThan(MAX_MILLIS)   // seconds before: on a phone, an ANR
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val MAX_MILLIS = 1_000L
    }
}
