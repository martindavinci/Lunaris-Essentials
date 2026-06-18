package tools.lunaris.texteditor.feature.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PrettyLocationTest {
    @Test fun treeRoot_showsFolderName() {
        assertThat(prettyLocation("content://com.android.externalstorage.documents/tree/primary%3ADownload"))
            .isEqualTo("Download")
    }

    @Test fun subfolder_showsRelativePath() {
        assertThat(
            prettyLocation("content://com.android.externalstorage.documents/tree/primary%3ADownload/document/primary%3ADownload%2Fsub")
        ).isEqualTo("Download/sub")
    }

    @Test fun plainPath_isTrimmed() {
        assertThat(prettyLocation("/storage/emulated/0/Documents")).isEqualTo("storage/emulated/0/Documents")
    }
}
