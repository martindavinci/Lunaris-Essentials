package io.github.martindavinci.lunaris.pdf.pages

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test

class PageJobTest {
    @Test
    fun `a job says where each page comes from and how it turns`() {
        val job = PageJob(
            sources = listOf(SourceKind.PDF, SourceKind.JPEG),
            outputs = listOf(
                listOf(
                    PageSlot(1, SlotContent.PageOf(0, 4), 90),
                    PageSlot(2, SlotContent.Blank(100f, 200f)),
                    PageSlot(3, SlotContent.PictureOf(1, 0), 270),
                ),
            ),
        )
        val json = JSONObject(job.encode())
        assertThat(json.getJSONArray("sources").getString(1)).isEqualTo("jpeg")
        val pages = json.getJSONArray("outputs").getJSONArray(0)
        assertThat(pages.getJSONObject(0).getInt("source")).isEqualTo(0)
        assertThat(pages.getJSONObject(0).getInt("index")).isEqualTo(4)
        assertThat(pages.getJSONObject(0).getInt("turn")).isEqualTo(90)
        assertThat(pages.getJSONObject(1).getDouble("width")).isEqualTo(100.0)
        assertThat(pages.getJSONObject(1).has("source")).isFalse()
        assertThat(pages.getJSONObject(2).getInt("source")).isEqualTo(1)
        assertThat(pages.getJSONObject(2).getInt("turn")).isEqualTo(270)
    }

    @Test
    fun `the count of pages covers every output`() {
        val page = PageSlot(1, SlotContent.PageOf(0, 0))
        assertThat(PageJob(listOf(SourceKind.PDF), listOf(listOf(page, page), listOf(page))).pageCount).isEqualTo(3)
    }
}
