package io.github.martindavinci.lunaris.archives.session

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.engine.ArchiveProblem
import io.github.martindavinci.lunaris.archives.engine.EngineAccessDeniedException
import io.github.martindavinci.lunaris.archives.engine.EngineRoute
import io.github.martindavinci.lunaris.archives.engine.EngineStoppedException
import io.github.martindavinci.lunaris.archives.engine.ListResult
import io.github.martindavinci.lunaris.archives.safety.Limits
import io.github.martindavinci.lunaris.archives.testing.FakeArchiveEngine
import io.github.martindavinci.lunaris.archives.testing.index
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchiveSessionTest {
    @get:Rule val temp = TemporaryFolder()
    private val engine = FakeArchiveEngine()
    private val passwords = PasswordMemory()
    private val session by lazy { ArchiveSession(engine, IndexCache(temp.root), passwords, IncomingCopy(temp.root)) }
    private val listed = ListResult.Listed(index("a.txt"))

    private fun source(n: Int = 1, kind: ArchiveKind = ArchiveKind.ZIP) =
        ArchiveSource("content://a/$n", "a$n.zip", 10, 20, "%016x".format(n), kind) { error("not used") }
    private fun answer(route: EngineRoute, vararg results: ListResult) {
        engine.lists.getOrPut(route) { ArrayDeque() }.addAll(results)
    }
    private fun opened(outcome: OpenOutcome) = (outcome as OpenOutcome.Opened).archive

    @Test fun theIndexCacheIsReadAndWrittenOffTheCallersThread() = runTest {
        val counting = object : kotlinx.coroutines.CoroutineDispatcher() {
            var used = 0
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { used++; block.run() }
        }
        val background = ArchiveSession(engine, IndexCache(temp.root), passwords, IncomingCopy(temp.root), counting)
        engine.lists.getOrPut(EngineRoute.LIBARCHIVE) { ArrayDeque() } += ListResult.Listed(index("a.md"))
        background.open(source())
        assertThat(counting.used).isAtLeast(2)   // the cache read that missed, then the write
    }
    @Test fun closeAll_forgetsEveryArchiveAndItsPassword() = runTest {
        answer(EngineRoute.LIBARCHIVE, ListResult.Listed(index("a.md")))
        val a = opened(session.open(source()))
        session.keepPassword(a.id, "pw".toCharArray(), remember = false)
        session.closeAll()
        assertThat(session.get(a.id)).isNull(); assertThat(session.passwordFor(a.id)).isNull()
    }
    @Test fun aListingReadFromACopy_isNotSaved_soTheCopyIsMadeAgain() = runTest {
        answer(EngineRoute.LIBARCHIVE, ListResult.Listed(index("a.md")), ListResult.Listed(index("a.md")))
        session.open(source().copy(fromCopy = true))
        session.open(source())
        assertThat(engine.listCalls).hasSize(2)
    }
    @Test fun plainZip_opensWithLibarchive() = runTest {
        answer(EngineRoute.LIBARCHIVE, listed)
        assertThat(opened(session.open(source())).route).isEqualTo(EngineRoute.LIBARCHIVE)
    }
    @Test fun unknownKind_isNotAnArchive_withoutCallingTheEngine() = runTest {
        assertThat(session.open(source(kind = ArchiveKind.UNKNOWN))).isEqualTo(OpenOutcome.Problem(ArchiveProblem.NOT_ARCHIVE))
        assertThat(engine.listCalls).isEmpty()
    }
    @Test fun splitZip_isSplitArchive() = runTest {
        assertThat(session.open(source(kind = ArchiveKind.ZIP_SPLIT))).isEqualTo(OpenOutcome.Problem(ArchiveProblem.SPLIT_ARCHIVE))
    }
    @Test fun bigDictionary_reroutesToLibRar() = runTest {
        answer(EngineRoute.LIBARCHIVE, ListResult.Reroute(EngineRoute.LIB_RAR)); answer(EngineRoute.LIB_RAR, listed)
        assertThat(opened(session.open(source(kind = ArchiveKind.RAR5))).route).isEqualTo(EngineRoute.LIB_RAR)
    }
    @Test fun encryptedHeaders_askForThePassword() = runTest {
        answer(EngineRoute.LIBARCHIVE, ListResult.NeedsPassword(EngineRoute.LIB_RAR))
        val outcome = session.open(source(kind = ArchiveKind.RAR5)) as OpenOutcome.NeedsPassword
        assertThat(outcome.pending.route).isEqualTo(EngineRoute.LIB_RAR)
    }
    @Test fun rememberedPassword_isUsedWithoutAsking() = runTest {
        passwords.put(source().key, "pw".toCharArray())
        answer(EngineRoute.LIBARCHIVE, ListResult.NeedsPassword(EngineRoute.SEVEN_Z)); answer(EngineRoute.SEVEN_Z, listed)
        assertThat(opened(session.open(source())).route).isEqualTo(EngineRoute.SEVEN_Z)
        assertThat(engine.listCalls.last()).isEqualTo(EngineRoute.SEVEN_Z to "pw")
    }
    @Test fun unlockWithWrongPassword_staysPending() = runTest {
        answer(EngineRoute.SEVEN_Z, ListResult.WrongPassword)
        val pending = PendingOpen(source(), EngineRoute.SEVEN_Z, null, null)
        assertThat(session.unlock(pending, "bad".toCharArray(), remember = true)).isEqualTo(OpenOutcome.WrongPassword(pending))
        assertThat(passwords.get(source().key)).isNull()
    }
    @Test fun unlockWithRemember_storesThePassword() = runTest {
        answer(EngineRoute.SEVEN_Z, listed)
        session.unlock(PendingOpen(source(), EngineRoute.SEVEN_Z, null, null), "pw".toCharArray(), remember = true)
        assertThat(passwords.get(source().key)!!.concatToString()).isEqualTo("pw")
    }
    @Test fun thePasswordStaysWithTheOpenArchiveUntilItCloses() = runTest {
        answer(EngineRoute.SEVEN_Z, listed)
        val archive = opened(session.unlock(PendingOpen(source(), EngineRoute.SEVEN_Z, null, null), "pw".toCharArray(), remember = false))
        assertThat(passwords.get(source().key)).isNull()
        assertThat(session.passwordFor(archive.id)!!.concatToString()).isEqualTo("pw")
        session.close(archive.id)
        assertThat(session.passwordFor(archive.id)).isNull()
    }
    @Test fun cachedIndex_skipsTheEngine() = runTest {
        answer(EngineRoute.LIBARCHIVE, listed)
        session.open(source()); session.open(source())
        assertThat(engine.listCalls).hasSize(1)
    }
    @Test fun encryptedNames_areNotCached() = runTest {
        val secret = ListResult.Listed(index("a.txt").copy(encryption = Encryption.HEADERS))
        answer(EngineRoute.SEVEN_Z, secret, secret)
        val pending = PendingOpen(source(), EngineRoute.SEVEN_Z, null, null)
        session.unlock(pending, "pw".toCharArray(), remember = false)
        session.unlock(pending, "pw".toCharArray(), remember = false)
        assertThat(engine.listCalls).hasSize(2)
    }
    @Test fun accessDenied_asksForACopy() = runTest {
        engine.listFailures += EngineAccessDeniedException()
        assertThat(session.open(source())).isEqualTo(OpenOutcome.NeedsCopy("content://a/1", 10))
    }
    @Test fun engineStopped_isReaderStopped() = runTest {
        engine.listFailures += EngineStoppedException()
        assertThat(session.open(source())).isEqualTo(OpenOutcome.Problem(ArchiveProblem.READER_STOPPED))
    }
    @Test fun nestingDeeperThanTheLimit_isRefused() = runTest {
        var parent: String? = null
        repeat(Limits.MAX_NESTING) { n ->
            answer(EngineRoute.LIBARCHIVE, listed)
            parent = opened(session.open(source(n + 1), parentId = parent, nestedEntry = 0)).id
        }
        assertThat(session.open(source(99), parentId = parent, nestedEntry = 0)).isEqualTo(OpenOutcome.Problem(ArchiveProblem.UNSUPPORTED_FEATURE))
    }
    @Test fun reroutesNeverLoop() = runTest {
        repeat(5) { answer(EngineRoute.LIBARCHIVE, ListResult.Reroute(EngineRoute.LIBARCHIVE)) }
        assertThat(session.open(source())).isEqualTo(OpenOutcome.Problem(ArchiveProblem.DAMAGED))
    }
    @Test fun evictsTheOldestBeyondTheMemoryLimit() = runTest {
        val capacity = Limits.INDEXES_IN_MEMORY * (Limits.MAX_NESTING + 1)
        val ids = (1..capacity + 1).map { n -> answer(EngineRoute.LIBARCHIVE, listed); opened(session.open(source(n))).id }
        assertThat(session.get(ids.first())).isNull()
        assertThat(session.get(ids.last())).isNotNull()
    }
    @Test fun closingAParent_closesItsChildren() = runTest {
        answer(EngineRoute.LIBARCHIVE, listed, listed)
        val parent = opened(session.open(source(1))).id
        val child = opened(session.open(source(2), parentId = parent, nestedEntry = 0)).id
        session.close(parent)
        assertThat(session.get(child)).isNull()
    }
    @Test fun requests_areKeptById() {
        val id = session.register(OpenRequest("content://a/1", null, null, null))
        assertThat(session.request(id)).isEqualTo(OpenRequest("content://a/1", null, null, null))
    }
}
