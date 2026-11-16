package io.github.martindavinci.lunaris.archives.browse

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.testing.index
import org.junit.Test

class EntrySearchTest {
    private val view = FolderView(index("plan.pdf", "docs/plan.pdf", "drafts/plan-old.md", "img/floor-plan.png", "docs/other.md"))

    @Test fun matchesNamesInEveryFolder_caseInsensitive() {
        val hits = EntrySearch.search(view, "PLAN")
        assertThat(hits.map { it.row.path.joinToString("/") }).containsExactly("plan.pdf", "docs/plan.pdf", "drafts/plan-old.md", "img/floor-plan.png")
        assertThat(hits.single { it.row.name == "floor-plan.png" }.match).isEqualTo(6..9)
        assertThat(hits.last().row.path).containsExactly("plan.pdf")
    }
    @Test fun foldersComeBeforeTheirContents() {
        assertThat(EntrySearch.search(view, "d").map { it.row.path.joinToString("/") })
            .containsExactly("docs", "docs/plan.pdf", "docs/other.md", "drafts", "drafts/plan-old.md", "plan.pdf").inOrder()
    }
    @Test fun matchesFolderNamesToo() {
        assertThat(EntrySearch.search(view, "draft").map { it.row.name }).containsExactly("drafts")
    }
    @Test fun surroundingSpaces_areIgnored() {
        assertThat(EntrySearch.search(view, " other ").map { it.row.name }).containsExactly("other.md")
    }
    @Test fun blankQuery_findsNothing() { assertThat(EntrySearch.search(view, "  ")).isEmpty() }
    @Test fun limitIsRespected() { assertThat(EntrySearch.search(view, "p", limit = 2)).hasSize(2) }
}
