package io.github.martindavinci.lunaris.scanner.history

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.FailingDataStore
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import io.github.martindavinci.lunaris.scanner.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HistoryRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "test.preferences_pb") }
    }
    private val settings by lazy { SettingsStore(dataStore) }
    private val repository by lazy { HistoryRepository(dataStore, settings) }

    private fun code(text: String) = ScannedCode(text, CodeFormat.QR_CODE)

    @After fun tearDown() = scope.cancel()

    @Test fun startsEmpty() = runBlocking<Unit> {
        assertThat(repository.entries.first()).isEmpty()
    }

    @Test fun add_storesTheNewestFirst() = runBlocking<Unit> {
        repository.add(code("first"))
        repository.add(code("second"))

        assertThat(repository.entries.first().map { it.text }).containsExactly("second", "first").inOrder()
    }

    @Test fun add_consecutiveDuplicate_keepsOneEntry() = runBlocking<Unit> {
        repository.add(code("same"))
        repository.add(code("same"))

        assertThat(repository.entries.first()).hasSize(1)
    }

    @Test fun add_withHistoryOff_doesNothing() = runBlocking<Unit> {
        settings.setKeepHistory(false)

        repository.add(code("private"))

        assertThat(repository.entries.first()).isEmpty()
    }

    @Test fun turningHistoryOff_keepsWhatIsAlreadyStored() = runBlocking<Unit> {
        repository.add(code("kept"))

        settings.setKeepHistory(false)

        assertThat(repository.entries.first().map { it.text }).containsExactly("kept")
    }

    @Test fun delete_removesTheEntry() = runBlocking<Unit> {
        repository.add(code("gone"))
        repository.add(code("kept"))
        val goneId = repository.entries.first().first { it.text == "gone" }.id

        repository.delete(goneId)

        assertThat(repository.entries.first().map { it.text }).containsExactly("kept")
    }

    @Test fun clear_removesEverything() = runBlocking<Unit> {
        repository.add(code("a"))
        repository.add(code("b"))

        repository.clear()

        assertThat(repository.entries.first()).isEmpty()
    }

    @Test fun partlyCorruptData_keepsWhatItCanAndAcceptsNewEntries() = runBlocking<Unit> {
        dataStore.edit {
            it[stringPreferencesKey("history_entries")] =
                """["junk",{"id":4,"text":"old","format":"QR_CODE","timestampMillis":5}]"""
        }

        repository.add(code("new"))

        val entries = repository.entries.first()
        assertThat(entries.map { it.text }).containsExactly("new", "old").inOrder()
        assertThat(entries.first().id).isEqualTo(5)
    }

    @Test fun unreadableFile_readsAsEmpty() = runBlocking<Unit> {
        val store = FailingDataStore(readWorks = false)
        val broken = HistoryRepository(store, SettingsStore(store))

        assertThat(broken.entries.first()).isEmpty()
    }

    @Test fun failedWrites_doNotReachTheCaller() = runBlocking<Unit> {
        val store = FailingDataStore(readWorks = true)
        val broken = HistoryRepository(store, SettingsStore(store))

        broken.add(code("lost"))
        broken.delete(1)
        broken.clear()

        assertThat(broken.entries.first()).isEmpty()
    }
}
