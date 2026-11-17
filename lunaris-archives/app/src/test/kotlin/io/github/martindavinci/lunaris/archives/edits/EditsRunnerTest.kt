package io.github.martindavinci.lunaris.archives.edits

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EditsRunnerTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun aDecisionThatThrowsIo_endsFailed_notInACrash() {
        val files = temp.newFolder()
        val store = EditStore(files)
        val watcher = EditWatcher(store, files, {}, Dispatchers.Unconfined)
        val io = FileUpdateIo()
        val actions = EditActions(store, watcher, ZipUpdater(io, UpdateJournal(files), temp.newFolder()) { Long.MAX_VALUE }, io, {}, { null },
            object : SaveIo {
                override fun write(target: String, from: File) = Unit
                override fun createIn(tree: String, name: String, mime: String) = name
            }) { _, _, _ -> }
        val runner = EditsRunner(actions, Dispatchers.Unconfined)
        runner.run(EditsRunner.Work("u", "a.zip")) { throw IOException("No space left on device") }
        assertThat(runner.result.value?.outcome).isEqualTo(EditOutcome.Failed)
        assertThat(runner.working.value).isNull()
    }
}
