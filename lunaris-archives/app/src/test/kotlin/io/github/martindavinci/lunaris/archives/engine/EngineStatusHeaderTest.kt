package io.github.martindavinci.lunaris.archives.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class EngineStatusHeaderTest {
    private val header = File("../native/jni/engine_status.h").readText()
    private fun codes(prefix: String) =
        Regex("""#define ${prefix}_([A-Z0-9_]+) (\d+)""").findAll(header).associate { it.groupValues[1] to it.groupValues[2].toInt() }

    @Test fun cHeaderMatchesKotlin() {
        assertThat(codes("ENGINE")).isEqualTo(EngineStatus.entries.associate { it.name to it.code })
    }
    @Test fun entryOutcomesMatchKotlin() {
        assertThat(codes("ENTRY")).isEqualTo(EntryOutcome.entries.associate { it.name to it.code })
    }
}
