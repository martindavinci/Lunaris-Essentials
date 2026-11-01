package tools.lunaris.texteditor.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/** The licenses screen shows assets/licenses/NOTICES.txt: it must cover what the app bundles. */
class NoticesTest {
    private val notices = File("src/main/assets/licenses/NOTICES.txt").readText()

    @Test fun everyBundledGrammar_hasANotice() {
        val grammars = File("src/main/assets/textmate/grammars").list()!!.filter { it.endsWith(".json") }
        // Covered by the TextMate bundle terms and the MATLAB BSD license in the notices.
        val coveredByName = setOf("toml.json", "yaml.json", "matlab.json")
        val missing = grammars.filter { it !in coveredByName && !Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(notices) }
        assertThat(missing).isEmpty()
    }

    @Test fun includesEveryLicenseText() {
        listOf(
            "Apache License\n                           Version 2.0",
            "GNU LESSER GENERAL PUBLIC LICENSE\n                       Version 2.1",
            "Eclipse Public License - v 2.0",
            "Copyright (c) 2015, Atlassian Pty Ltd",
            "Copyright (c) 2006, Ivan Sagalaev.",
            "Copyright 2018 The MathWorks, Inc.",
        ).forEach { assertThat(notices).contains(it) }
    }
}
