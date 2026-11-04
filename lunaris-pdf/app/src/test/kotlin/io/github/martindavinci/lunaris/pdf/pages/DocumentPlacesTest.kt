package io.github.martindavinci.lunaris.pdf.pages

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DocumentPlacesTest {
    @Test
    fun `the phone's storage tells its folder`() {
        assertThat(DocumentPlaces.folderOf("primary:Download/moon.pdf")).isEqualTo("Download")
        assertThat(DocumentPlaces.folderOf("primary:Documents/Work/moon.pdf")).isEqualTo("Work")
        assertThat(DocumentPlaces.folderOf("1A2B-3C4D:Scans/moon.pdf")).isEqualTo("Scans")
    }

    @Test
    fun `others do not`() {
        listOf("primary:moon.pdf", "document:1234", "msf:55", "acc=1;doc=33", "").forEach {
            assertThat(DocumentPlaces.folderOf(it)).isNull()
        }
    }
}
