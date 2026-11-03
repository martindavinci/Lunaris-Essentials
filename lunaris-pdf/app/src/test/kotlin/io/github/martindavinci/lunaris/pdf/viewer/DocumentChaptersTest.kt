package io.github.martindavinci.lunaris.pdf.viewer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DocumentChaptersTest {
    private val outline = listOf(
        OutlineItem("1. Light", 2, 0),
        OutlineItem("Old ideas", 5, 1),
        OutlineItem("2. Leonardo", 8, 0),
        OutlineItem("The sketch", 10, 1),
        OutlineItem("No page", null, 0),
        OutlineItem("3. Today", 14, 0),
    )

    @Test fun chapter_isTheLastTopLevelEntryAtOrBeforeThePage() {
        assertThat(DocumentChapters.chapterAt(outline, 12)?.title).isEqualTo("2. Leonardo")
        assertThat(DocumentChapters.chapterAt(outline, 14)?.title).isEqualTo("3. Today")
    }

    @Test fun chapter_isNullBeforeTheFirstEntry() {
        assertThat(DocumentChapters.chapterAt(outline, 1)).isNull()
    }

    @Test fun chapter_isNullWithoutAnOutline() {
        assertThat(DocumentChapters.chapterAt(emptyList(), 3)).isNull()
    }

    @Test fun chapter_usesTheShallowestLevelTheDocumentHas() {
        val deep = listOf(OutlineItem("Only", 1, 2), OutlineItem("Deeper", 3, 3))
        assertThat(DocumentChapters.chapterAt(deep, 5)?.title).isEqualTo("Only")
    }

    @Test fun current_isTheLastEntryOfAnyLevelAtOrBeforeThePage() {
        assertThat(DocumentChapters.currentIndex(outline, 12)).isEqualTo(3)
        assertThat(DocumentChapters.currentIndex(outline, 1)).isEqualTo(-1)
    }
}
