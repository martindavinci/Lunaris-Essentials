package io.github.martindavinci.lunaris.archives.recents

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.engine.EngineRoute
import io.github.martindavinci.lunaris.archives.safety.Limits
import io.github.martindavinci.lunaris.archives.session.ArchiveSource
import io.github.martindavinci.lunaris.archives.session.OpenArchive
import io.github.martindavinci.lunaris.archives.settings.AppSettings
import io.github.martindavinci.lunaris.archives.testing.index
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecentsRecorderTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store by lazy { RecentsStore(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "r.preferences_pb") }) }
    private val settings = MutableStateFlow(AppSettings())
    private val permissions = object : UriPermissions {
        var allow = true
        val released = mutableListOf<String>()
        override fun takeRead(uri: String) = allow
        override fun release(uri: String) { released += uri }
    }
    private val recorder by lazy { RecentsRecorder(store, settings, permissions) { 7 } }
    private fun archive(n: Int = 1, parent: String? = null, encryption: Encryption = Encryption.NONE) = OpenArchive(
        "id$n", ArchiveSource("content://a/$n", "a$n.zip", 9, 1, "%016x".format(n), ArchiveKind.ZIP) { error("unused") },
        EngineRoute.LIBARCHIVE, index("a.txt").copy(encryption = encryption), parent, null,
    )

    @After fun stop() = runBlocking { scope.coroutineContext.job.cancelAndJoin() }

    @Test fun permissionRefused_isNotRemembered() = runBlocking<Unit> {
        permissions.allow = false
        recorder.visited(archive(), listOf("docs"))
        assertThat(store.recents.first()).isEmpty()
    }
    @Test fun granted_isRememberedWithItsPath() = runBlocking<Unit> {
        recorder.visited(archive(), listOf("docs"))
        val item = store.recents.first().single()
        assertThat(item.lastPath).containsExactly("docs"); assertThat(item.lastOpenedMillis).isEqualTo(7); assertThat(item.locked).isFalse()
    }
    @Test fun encryptedNames_leaveNoPath() = runBlocking<Unit> {
        recorder.visited(archive(encryption = Encryption.HEADERS), listOf("docs"))
        assertThat(store.recents.first().single().lastPath).isNull()
        assertThat(store.recents.first().single().locked).isTrue()
    }
    @Test fun nestedArchives_areNotRemembered() = runBlocking<Unit> {
        recorder.visited(archive(parent = "id0"), emptyList())
        assertThat(store.recents.first()).isEmpty()
    }
    @Test fun rememberOff_isNotRemembered() = runBlocking<Unit> {
        settings.value = AppSettings(rememberRecents = false)
        recorder.visited(archive(), emptyList())
        assertThat(store.recents.first()).isEmpty()
    }
    @Test fun eviction_releasesThePermission() = runBlocking<Unit> {
        (1..Limits.RECENTS_MAX + 1).forEach { recorder.visited(archive(it), emptyList()) }
        assertThat(permissions.released).containsExactly("content://a/1")
    }
}
