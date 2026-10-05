package tools.lunaris.texteditor.file

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PrivatePathsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val private by lazy { tmp.newFolder("data", "app") }
    private val roots by lazy { listOf(private) }

    @Test fun fileInsidePrivateDir_isPrivate() {
        assertThat(isInsideAny(File(private, "files/datastore/prefs.pb"), roots)).isTrue()
    }

    @Test fun traversalIntoPrivateDir_isPrivate() {
        val sneaky = File(tmp.root, "public/../data/app/files/x.txt")
        assertThat(isInsideAny(sneaky, roots)).isTrue()
    }

    @Test fun siblingWithSharedPrefix_isNotPrivate() {
        assertThat(isInsideAny(File(tmp.root, "data/app2/x.txt"), roots)).isFalse()
    }

    @Test fun publicFile_isNotPrivate() {
        assertThat(isInsideAny(File(tmp.root, "storage/notes.txt"), roots)).isFalse()
    }
}
