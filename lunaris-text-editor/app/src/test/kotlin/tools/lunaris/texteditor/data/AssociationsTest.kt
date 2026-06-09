package tools.lunaris.texteditor.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AssociationsTest {
    @Test fun put_overwritesByExtension_caseInsensitive() {
        val a = Associations.empty().put("KT", "kotlin").put("kt", "java")
        assertThat(a.languageFor("kt")).isEqualTo("java")
        assertThat(a.entries).hasSize(1)
    }
    @Test fun remove_dropsExtension() {
        assertThat(Associations.empty().put("md", "markdown").remove("md").languageFor("md")).isNull()
    }
    @Test fun jsonRoundTrip() {
        val a = Associations.empty().put("kt", "kotlin").put("py", "python")
        assertThat(Associations.fromJson(a.toJson())).isEqualTo(a)
    }
    @Test fun fromJson_blank_isEmpty() {
        assertThat(Associations.fromJson("")).isEqualTo(Associations.empty())
    }
}
