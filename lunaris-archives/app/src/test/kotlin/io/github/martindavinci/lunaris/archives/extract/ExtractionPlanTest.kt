package io.github.martindavinci.lunaris.archives.extract

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.safety.SafePath
import io.github.martindavinci.lunaris.archives.safety.SkipReason
import io.github.martindavinci.lunaris.archives.testing.index
import org.junit.Test

class ExtractionPlanTest {
    private val idx = index("docs/a.md", "docs/sub/b:c.md", "empty/", "../evil", "link", "top.txt")
    private val withLink = idx.copy(entries = idx.entries.map {
        if (it.displayPath == "link") it.copy(type = EntryType.SYMLINK, path = SafePath.evaluate("link", EntryType.SYMLINK)) else it
    })

    @Test fun everything_writesSafeFilesAndListsTheRest() {
        val plan = ExtractionPlan.of(withLink.entries)
        assertThat(plan.files.map { it.folder + it.name }).containsExactly(listOf("docs", "a.md"), listOf("docs", "sub", "b_c.md"), listOf("top.txt"))
        assertThat(plan.folders).containsExactly(listOf("docs"), listOf("docs", "sub"), listOf("empty")).inOrder()
        assertThat(plan.skipped).containsExactly(SkippedEntry("../evil", SkipReason.OUTSIDE_FOLDER), SkippedEntry("link", SkipReason.LINK))
        assertThat(plan.declaredBytes).isEqualTo(3 * 1024)
    }
    @Test fun aFolderSelection_expandsToItsContent() {
        val docs = withLink.entries.filter { it.displayPath.startsWith("docs/") }
        val plan = ExtractionPlan.of(docs)
        assertThat(plan.files).hasSize(2)
        assertThat(plan.folders).containsExactly(listOf("docs"), listOf("docs", "sub")).inOrder()
    }
    @Test fun aComponentWithNothingWritableLeft_isAnInvalidName() {
        val plan = ExtractionPlan.of(index("ok/.../x.txt", "a/. ./x").entries)
        assertThat(plan.files).isEmpty()
        assertThat(plan.skipped).containsExactly(
            SkippedEntry("ok/.../x.txt", SkipReason.INVALID_NAME), SkippedEntry("a/. ./x", SkipReason.INVALID_NAME))
        assertThat(plan.folders).isEmpty()
    }
}
