package tools.lunaris.texteditor.syntax

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.charset.StandardCharsets

class CharsetCatalogTest {

    private val catalog = CharsetCatalog()

    @Test
    fun containsCommonCharsets_withUtf8First() {
        assertThat(catalog.entries.first().charset).isEqualTo(StandardCharsets.UTF_8)
        assertThat(catalog.entries.size).isAtLeast(20)
    }

    @Test
    fun allEntriesAreSupportedOnThisJvm() {
        assertThat(catalog.entries.all { java.nio.charset.Charset.isSupported(it.charset.name()) }).isTrue()
    }

    @Test
    fun findByName_roundTrips() {
        val utf16 = catalog.findByName("UTF-16")
        assertThat(utf16).isNotNull()
        assertThat(utf16!!.charset).isEqualTo(StandardCharsets.UTF_16)
    }

    @Test
    fun noDuplicateDisplayNames() {
        val names = catalog.entries.map { it.display }
        assertThat(names).containsNoDuplicates()
    }
}
