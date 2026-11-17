package io.github.martindavinci.lunaris.archives.feature.browser

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.engine.EngineRoute
import io.github.martindavinci.lunaris.archives.files.CopyResult
import io.github.martindavinci.lunaris.archives.session.ArchiveSession
import io.github.martindavinci.lunaris.archives.session.ArchiveSource
import io.github.martindavinci.lunaris.archives.session.IncomingCopy
import io.github.martindavinci.lunaris.archives.session.IndexCache
import io.github.martindavinci.lunaris.archives.session.OpenArchive
import io.github.martindavinci.lunaris.archives.session.PasswordMemory
import io.github.martindavinci.lunaris.archives.testing.FakeArchiveEngine
import io.github.martindavinci.lunaris.archives.testing.FakeEntryOpener
import io.github.martindavinci.lunaris.archives.testing.index
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserSharingTest {
    @get:Rule val temp = TemporaryFolder()
    private val scope = TestScope()
    private val opener = FakeEntryOpener()
    private val state = MutableStateFlow(BrowserUiState())
    private val sharing by lazy {
        BrowserSharing(scope, ArchiveSession(FakeArchiveEngine(), IndexCache(temp.root), PasswordMemory(), IncomingCopy(temp.root)), opener, state)
    }
    private val archive = OpenArchive(
        "id", ArchiveSource("content://a/notes.zip", "notes.zip", 1, 1, "0123456789abcdef", ArchiveKind.ZIP) { error("unused") },
        EngineRoute.LIBARCHIVE, index("a.png", "b.jpg", "c.md"), null, null,
    )
    private fun ready(name: String, mime: String) = CopyResult.Ready("content://x/$name", mime, "/x/$name")

    @Test fun twoImages_areSentTogetherAsImages() {
        opener.results += ready("a.png", "image/png"); opener.results += ready("b.jpg", "image/jpeg")
        sharing.share(archive, archive.index.entries.take(2)); scope.advanceUntilIdle()
        assertThat(state.value.launch).isEqualTo(LaunchRequest.Send(listOf("content://x/a.png", "content://x/b.jpg"), "image/*"))
    }
    @Test fun theChosenFiles_areCopiedInOneBatch() {
        opener.results += ready("a.png", "image/png"); opener.results += ready("b.jpg", "image/jpeg")
        sharing.share(archive, archive.index.entries.take(2)); scope.advanceUntilIdle()
        assertThat(opener.batches).containsExactly(2)
    }
    @Test fun mixedKinds_areSentAsAnything() {
        opener.results += ready("a.png", "image/png"); opener.results += ready("c.md", "text/markdown")
        sharing.share(archive, listOf(archive.index.entries[0], archive.index.entries[2])); scope.advanceUntilIdle()
        assertThat((state.value.launch as LaunchRequest.Send).mime).isEqualTo("*/*")
    }
    @Test fun oneFailure_sendsNothingAndSaysSo() {
        opener.results += ready("a.png", "image/png"); opener.results += CopyResult.Damaged
        sharing.share(archive, archive.index.entries.take(2)); scope.advanceUntilIdle()
        assertThat(state.value.launch).isNull(); assertThat(state.value.notice).isEqualTo(BrowserNotice.ShareFailed)
    }
    @Test fun theArchiveItself_isSentAsItCame() {
        sharing.shareArchive(archive)
        assertThat(state.value.launch).isEqualTo(LaunchRequest.Send(listOf("content://a/notes.zip"), "application/zip"))
    }
}
