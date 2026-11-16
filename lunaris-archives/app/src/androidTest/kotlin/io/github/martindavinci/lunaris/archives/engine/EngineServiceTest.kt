// Run by the user on a device or emulator: gradlew :app:connectedDebugAndroidTest (not run by the build).
package io.github.martindavinci.lunaris.archives.engine

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.ArchiveSignatures
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.safety.SafePath
import io.github.martindavinci.lunaris.archives.safety.SkipReason
import io.github.martindavinci.lunaris.archives.session.ArchiveKeys
import io.github.martindavinci.lunaris.archives.session.ArchiveSource
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** The real engine in its isolated process, on a device: what no JVM test can run. */
@RunWith(AndroidJUnit4::class)
class EngineServiceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val engine = EngineClient(EngineConnection(context))

    private fun source(file: File): ArchiveSource {
        val head = file.readBytes().copyOf(ArchiveSignatures.HEAD_BYTES.coerceAtMost(file.length().toInt()))
        return ArchiveSource(
            uri = file.toURI().toString(), name = file.name, size = file.length(), modifiedMillis = file.lastModified(),
            key = ArchiveKeys.of(file.path, file.length(), file.lastModified()),
            kindHint = ArchiveSignatures.detect(head, file.name),
        ) { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
    }

    private fun asset(name: String): File {
        val file = File(context.cacheDir, name)
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return file
    }

    private fun listed(name: String) = runBlocking {
        engine.list(source(asset(name)), EngineRoute.LIBARCHIVE, password = null) as ListResult.Listed
    }

    @Test fun plainZip_listsAndExtracts() {
        val src = source(asset("plain.zip"))
        val index = (runBlocking { engine.list(src, EngineRoute.LIBARCHIVE, null) } as ListResult.Listed).index
        assertThat(index.entries).hasSize(7)
        val readme = index.entries.first { it.displayPath == "README.md" }
        val events = runBlocking {
            engine.extract(src, EngineRoute.LIBARCHIVE, null, longArrayOf(readme.index), mapOf(readme.index to readme.rawPath)).toList()
        }
        val bytes = ByteArrayOutputStream()
        events.filterIsInstance<EngineEvent.Bytes>().forEach { bytes.write(it.chunk, 0, it.length) }
        assertThat(bytes.toString(Charsets.UTF_8.name())).isEqualTo("# Project notes\n")
        assertThat(events.last()).isEqualTo(EngineEvent.Finished(EngineStatus.OK))
    }

    @Test fun hostileTar_marksLinksAndEscapes() {
        val entries = listed("hostile.tar").index.entries
        assertThat(entries.first { it.displayPath == "abs-link" }.type).isEqualTo(EntryType.SYMLINK)
        assertThat(entries.first { it.displayPath == "../escape.txt" }.path).isEqualTo(SafePath.Result.Skipped(SkipReason.OUTSIDE_FOLDER))
    }

    @Test fun tarGz_isLabelledTarGz() {
        val index = listed("plain.tar.gz").index
        assertThat(index.kind).isEqualTo(ArchiveKind.GZIP)
        assertThat(index.formatLabel).isEqualTo("TAR.GZ")
    }

    @Test fun oneByteOfText_isNotAnArchive() {
        val file = File(context.cacheDir, "one.txt").apply { writeText("x") }
        val result = runBlocking { engine.list(source(file), EngineRoute.LIBARCHIVE, null) }
        assertThat(result).isEqualTo(ListResult.Problem(ArchiveProblem.NOT_ARCHIVE))
    }
}
