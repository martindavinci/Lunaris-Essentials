package io.github.martindavinci.lunaris.archives.engine

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.ArchiveEntry
import io.github.martindavinci.lunaris.archives.archive.ArchiveIndex
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.archive.RawPath
import io.github.martindavinci.lunaris.archives.safety.SafePath
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Test

class ListMappingTest {
    private val idx = ArchiveIndex(
        ArchiveKind.ZIP, "ZIP",
        listOf(ArchiveEntry(0, RawPath.of("a.txt".toByteArray()), "a.txt", SafePath.check("a.txt"), EntryType.FILE, 1, 1, null, false, null)),
        Encryption.NONE, null, null, cappedAtLimit = false, partial = false,
    )
    private fun map(status: EngineStatus, kind: ArchiveKind = ArchiveKind.ZIP, index: ArchiveIndex? = null) =
        ListMapping.toResult(status, kind, EngineRoute.LIBARCHIVE, index)

    @Test fun ok_isListed() {
        assertThat(map(EngineStatus.OK, index = idx)).isEqualTo(ListResult.Listed(idx))
        assertThat(map(EngineStatus.LIMIT_REACHED, index = idx)).isEqualTo(ListResult.Listed(idx))
        assertThat(map(EngineStatus.OK)).isEqualTo(ListResult.Problem(ArchiveProblem.DAMAGED))
    }
    @Test fun encryptedHeaders_askForThePasswordOnTheRightEngine() {
        assertThat(map(EngineStatus.HEADERS_ENCRYPTED, ArchiveKind.RAR5)).isEqualTo(ListResult.NeedsPassword(EngineRoute.LIB_RAR))
        assertThat(map(EngineStatus.HEADERS_ENCRYPTED, ArchiveKind.SEVEN_Z)).isEqualTo(ListResult.NeedsPassword(EngineRoute.SEVEN_Z))
        assertThat(map(EngineStatus.HEADERS_ENCRYPTED, ArchiveKind.TAR)).isEqualTo(ListResult.Problem(ArchiveProblem.UNSUPPORTED_FEATURE))
    }
    @Test fun encryptedData_isListedWhenThereIsAnIndex() {
        assertThat(map(EngineStatus.DATA_ENCRYPTED, index = idx)).isEqualTo(ListResult.Listed(idx))
        assertThat(map(EngineStatus.DATA_ENCRYPTED, ArchiveKind.SEVEN_Z)).isEqualTo(ListResult.NeedsPassword(EngineRoute.SEVEN_Z))
    }
    @Test fun bigDictionary_goesToLibRar() {
        assertThat(map(EngineStatus.DICTIONARY_TOO_LARGE, ArchiveKind.RAR5)).isEqualTo(ListResult.Reroute(EngineRoute.LIB_RAR))
    }
    @Test fun problems() {
        val p = { s: EngineStatus -> map(s, ArchiveKind.RAR4) }
        assertThat(p(EngineStatus.RAR4_ENCRYPTED)).isEqualTo(ListResult.Problem(ArchiveProblem.RAR4_PASSWORD))
        assertThat(p(EngineStatus.NOT_ARCHIVE)).isEqualTo(ListResult.Problem(ArchiveProblem.NOT_ARCHIVE))
        assertThat(p(EngineStatus.NEEDS_NEXT_VOLUME)).isEqualTo(ListResult.Problem(ArchiveProblem.SPLIT_ARCHIVE))
        assertThat(p(EngineStatus.OUT_OF_MEMORY)).isEqualTo(ListResult.Problem(ArchiveProblem.READER_STOPPED))
        assertThat(p(EngineStatus.UNSUPPORTED_FEATURE)).isEqualTo(ListResult.Problem(ArchiveProblem.UNSUPPORTED_FEATURE))
        assertThat(p(EngineStatus.PATH_MISMATCH)).isEqualTo(ListResult.Problem(ArchiveProblem.DAMAGED))
        assertThat(p(EngineStatus.WRONG_PASSWORD)).isEqualTo(ListResult.WrongPassword)
    }
    @Test fun damagedWithEntries_isAPartialListing() {
        val r = map(EngineStatus.TRUNCATED, index = idx) as ListResult.Listed
        assertThat(r.index.partial).isTrue()
        assertThat(map(EngineStatus.TRUNCATED)).isEqualTo(ListResult.Problem(ArchiveProblem.DAMAGED))
        assertThat(map(EngineStatus.DAMAGED, index = idx.copy(entries = emptyList()))).isEqualTo(ListResult.Problem(ArchiveProblem.DAMAGED))
    }
    @Test fun accessDeniedAndCancelled_throw() {
        assertThrows(EngineAccessDeniedException::class.java) { map(EngineStatus.ACCESS_DENIED) }
        assertThrows(CancellationException::class.java) { map(EngineStatus.CANCELLED) }
    }
}
