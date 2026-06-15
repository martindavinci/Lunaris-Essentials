package tools.lunaris.texteditor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tools.lunaris.texteditor.files.SafTreeBrowser

/**
 * Regression for the "Invalid URI ... DocumentFile.fromTreeUri" startup-of-browser crash.
 * A single-document content uri (e.g. the persisted permission from opening a file) must
 * NOT be treated as a browsable tree: docFor rejects non-tree uris, so list/parent degrade
 * to empty/null instead of throwing. Needs a real Android runtime (DocumentsContract).
 */
@RunWith(AndroidJUnit4::class)
class SafTreeBrowserInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val browser = SafTreeBrowser(context)

    // Shaped exactly like the crashing uri from the media-documents provider (no /tree/).
    private val documentUri =
        "content://com.android.providers.media.documents/document/document%3A1000135266"

    @Test
    fun list_documentUri_returnsEmpty_doesNotCrash() = runBlocking {
        assertTrue(browser.list(documentUri).isEmpty())
    }

    @Test
    fun parent_documentUri_returnsNull_doesNotCrash() = runBlocking {
        assertNull(browser.parent(documentUri))
    }
}
