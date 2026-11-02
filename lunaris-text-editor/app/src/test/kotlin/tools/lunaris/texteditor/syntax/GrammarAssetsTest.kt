package tools.lunaris.texteditor.syntax

import com.google.common.truth.Truth.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.io.File

/** tm4e casts every capture to a rule: a non-object value stops the grammar from highlighting at all. */
class GrammarAssetsTest {
    private val captureKeys = setOf("captures", "beginCaptures", "endCaptures", "whileCaptures")

    @Test fun everyCapture_isARule() {
        val grammars = File("src/main/assets/textmate/grammars").listFiles { file -> file.extension == "json" }!!
        assertThat(grammars).isNotEmpty()
        val invalid = grammars.flatMap { file ->
            invalidCaptures(JSONObject(file.readText()), file.name)
        }
        assertThat(invalid).isEmpty()
    }

    private fun invalidCaptures(node: Any?, path: String): List<String> = when (node) {
        is JSONObject -> node.keys().asSequence().toList().flatMap { key ->
            val value = node.get(key)
            val here = "$path/$key"
            val own = if (key in captureKeys && value is JSONObject) {
                value.keys().asSequence().filter { value.get(it) !is JSONObject }.map { "$here/$it" }.toList()
            } else {
                emptyList()
            }
            own + invalidCaptures(value, here)
        }
        is JSONArray -> (0 until node.length()).flatMap { invalidCaptures(node.get(it), "$path/$it") }
        else -> emptyList()
    }
}
