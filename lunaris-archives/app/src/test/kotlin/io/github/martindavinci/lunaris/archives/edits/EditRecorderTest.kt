package io.github.martindavinci.lunaris.archives.edits

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.engine.EngineRoute
import io.github.martindavinci.lunaris.archives.session.ArchiveSource
import io.github.martindavinci.lunaris.archives.session.OpenArchive
import io.github.martindavinci.lunaris.archives.testing.entry
import io.github.martindavinci.lunaris.archives.testing.index
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EditRecorderTest {
    @get:Rule val temp = TemporaryFolder()
    private val store by lazy { EditStore(temp.newFolder("files")) }
    private var facts: ArchiveFacts? = ArchiveFacts(100, 5, "fp")
    private val recorder by lazy { EditRecorder(store, { facts }) { 42 } }
    private val copy by lazy { temp.newFile("notes.md").apply { writeText("hello") } }

    private fun archive(kind: ArchiveKind = ArchiveKind.ZIP, parent: String? = null, fromCopy: Boolean = false, names: Encryption = Encryption.NONE) =
        OpenArchive(
            "id", ArchiveSource("content://a/1", "a.${kind.name.lowercase()}", 100, 5, "0123456789abcdef", kind, fromCopy) { error("unused") },
            EngineRoute.LIBARCHIVE, index("docs/notes.md", kind = kind).copy(encryption = names), parent, null,
        )

    @Test fun zip_isRecorded_updatable_withItsBaseline() = runBlocking<Unit> {
        assertThat(recorder.record(archive(), entry(0, "docs/notes.md"), copy)).isTrue()
        val r = store.all().single()
        assertThat(r.updatable).isTrue(); assertThat(r.entryPath).isEqualTo("docs/notes.md"); assertThat(r.archiveFingerprint).isEqualTo("fp")
        assertThat(r.size).isEqualTo(5); assertThat(r.sha256).isEqualTo(Fingerprint.sha256(copy)); assertThat(r.createdMillis).isEqualTo(42)
        assertThat(r.archiveKey).isEqualTo("0123456789abcdef")
    }
    @Test fun secondOpen_doesNotProbeOrHashAgain() = runBlocking<Unit> {
        recorder.record(archive(), entry(0, "docs/notes.md"), copy)
        facts = null   // a probe now would fail the record
        assertThat(recorder.record(archive(), entry(0, "docs/notes.md"), copy)).isTrue()
    }
    @Test fun sevenZ_isRecorded_butNotUpdatable() = runBlocking<Unit> {
        assertThat(recorder.record(archive(ArchiveKind.SEVEN_Z), entry(0, "docs/notes.md"), copy)).isTrue()
        assertThat(store.all().single().updatable).isFalse()
    }
    @Test fun encryptedNames_leaveNoRecord_andNoWrite() = runBlocking<Unit> {
        assertThat(recorder.record(archive(names = Encryption.HEADERS), entry(0, "docs/notes.md"), copy)).isFalse()
        assertThat(store.all()).isEmpty()
    }
    @Test fun nestedAndCopiedArchives_areNotWritable() = runBlocking<Unit> {
        assertThat(recorder.record(archive(parent = "p"), entry(0, "docs/notes.md"), copy)).isFalse()
        assertThat(recorder.record(archive(fromCopy = true), entry(0, "docs/notes.md"), copy)).isFalse()
        assertThat(store.all()).isEmpty()
    }
    @Test fun anArchiveThatCannotBeProbed_isNotWritable() = runBlocking<Unit> {
        facts = null
        assertThat(recorder.record(archive(), entry(0, "docs/notes.md"), copy)).isFalse()
    }
}
