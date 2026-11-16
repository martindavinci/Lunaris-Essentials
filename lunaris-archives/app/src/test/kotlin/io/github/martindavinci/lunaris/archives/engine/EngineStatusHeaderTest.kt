package io.github.martindavinci.lunaris.archives.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class EngineStatusHeaderTest {
    @Test fun cHeaderMatchesKotlin() {
        val header = File("../native/jni/engine_status.h").readText()
        val c = Regex("""#define ENGINE_([A-Z0-9_]+) (\d+)""").findAll(header).associate { it.groupValues[1] to it.groupValues[2].toInt() }
        assertThat(c).isEqualTo(EngineStatus.entries.associate { it.name to it.code })
    }
}
