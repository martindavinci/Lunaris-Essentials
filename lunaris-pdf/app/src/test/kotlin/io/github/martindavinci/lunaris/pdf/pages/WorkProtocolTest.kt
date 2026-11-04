package io.github.martindavinci.lunaris.pdf.pages

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkProtocolTest {
    @Test
    fun `commands call the page, with the job quoted`() {
        assertThat(WorkProtocol.encode(WorkCommand.Start("""{"a":"b"}""")))
            .isEqualTo("""window.lunarisWork.start("{\"a\":\"b\"}");""")
        assertThat(WorkProtocol.encode(WorkCommand.Ack(1, 2))).isEqualTo("window.lunarisWork.ack(1,2);")
        assertThat(WorkProtocol.encode(WorkCommand.AckOutput(3))).isEqualTo("window.lunarisWork.ackOutput(3);")
        assertThat(WorkProtocol.encode(WorkCommand.Cancel)).isEqualTo("window.lunarisWork.cancel();")
    }

    @Test
    fun `events come back`() {
        assertThat(WorkProtocol.decode("""{"type":"ready"}""")).isEqualTo(WorkEvent.Ready)
        assertThat(WorkProtocol.decode("""{"type":"progress","fraction":0.5,"page":3,"pages":9,"output":1}"""))
            .isEqualTo(WorkEvent.Progress(0.5f, 3, 9, 1))
        assertThat(WorkProtocol.decode("""{"type":"chunk","output":1,"index":0,"total":2,"data":"QQ=="}"""))
            .isEqualTo(WorkEvent.Chunk(1, 0, 2, "QQ=="))
        assertThat(WorkProtocol.decode("""{"type":"outputDone","output":1,"bytes":10}""")).isEqualTo(WorkEvent.OutputDone(1, 10))
        assertThat(WorkProtocol.decode("""{"type":"done"}""")).isEqualTo(WorkEvent.Done)
    }

    @Test
    fun `a failure says why and, if known, which source`() {
        assertThat(WorkProtocol.decode("""{"type":"failed","reason":"protected","source":1}"""))
            .isEqualTo(WorkEvent.Failed(WorkFailure.PROTECTED, 1))
        assertThat(WorkProtocol.decode("""{"type":"failed","reason":"memory"}"""))
            .isEqualTo(WorkEvent.Failed(WorkFailure.MEMORY, null))
    }

    @Test
    fun `an unknown reason still is a failure`() {
        assertThat(WorkProtocol.decode("""{"type":"failed","reason":"what"}"""))
            .isEqualTo(WorkEvent.Failed(WorkFailure.DAMAGED, null))
    }

    @Test
    fun `anything else is ignored`() {
        listOf(
            "",
            "nonsense",
            "[]",
            """{"type":"other"}""",
            """{"type":"chunk","output":0,"index":5,"total":2,"data":"QQ=="}""",
            """{"type":"chunk","output":0,"index":0,"total":1}""",
            """{"type":"outputDone","output":0,"bytes":-1}""",
            """{"type":"progress"}""",
        ).forEach { assertThat(WorkProtocol.decode(it)).isNull() }
    }
}
