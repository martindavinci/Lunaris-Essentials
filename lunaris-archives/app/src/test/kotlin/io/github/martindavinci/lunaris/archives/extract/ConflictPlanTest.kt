package io.github.martindavinci.lunaris.archives.extract

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.browse.FolderView
import io.github.martindavinci.lunaris.archives.testing.index
import org.junit.Test

class ConflictPlanTest {
    private val view = FolderView(index("notes.md", "Notes.MD", "docs/a.md", "new.txt"))
    private val plan = ExtractionPlan.of(view.entriesUnder(emptyList()))
    private val existing = mapOf(
        emptyList<String>() to ExistingNames(files = setOf("notes.md"), folders = emptySet()),
        listOf("docs") to ExistingNames(files = setOf("a.md"), folders = emptySet()),
    )
    private fun key(file: PlannedFile) = (file.folder + file.name).joinToString("/")

    @Test fun existingNames_caseInsensitive_andDuplicatesInsideTheArchive_areConflicts() {
        val conflicts = ConflictPlan.conflicts(plan, existing)
        assertThat(conflicts.map { key(it.file) }).containsExactly("notes.md", "Notes.MD", "docs/a.md")
    }
    @Test fun aDuplicateInsideTheArchive_namesTheFirstOne() {
        val conflict = ConflictPlan.conflicts(plan, emptyMap()).single()
        assertThat(conflict.file.name).isEqualTo("Notes.MD")
        assertThat(conflict.existingName).isEqualTo("notes.md")
    }
    @Test fun keepBoth_numbersFromKnownNames() {
        val choices = ConflictPlan.conflicts(plan, existing).associate { it.file to ConflictChoice.KEEP_BOTH }
        val out = ConflictPlan.resolve(plan, existing, choices).associate { key(it.file) to it.targetName }
        assertThat(out).containsExactly("notes.md", "notes (1).md", "Notes.MD", "Notes (2).MD", "docs/a.md", "a (1).md", "new.txt", "new.txt")
    }
    @Test fun noChoice_keepsBoth() {
        val out = ConflictPlan.resolve(plan, existing, emptyMap()).associate { key(it.file) to it.targetName }
        assertThat(out["docs/a.md"]).isEqualTo("a (1).md")
    }
    @Test fun replaceAndSkip() {
        val c = ConflictPlan.conflicts(plan, existing).associateBy { key(it.file) }
        val choices = mapOf(
            c.getValue("notes.md").file to ConflictChoice.REPLACE,
            c.getValue("Notes.MD").file to ConflictChoice.SKIP,
            c.getValue("docs/a.md").file to ConflictChoice.SKIP,
        )
        val resolved = ConflictPlan.resolve(plan, existing, choices)
        assertThat(resolved.single { it.file.name == "notes.md" }.replace).isTrue()
        assertThat(resolved.map { it.file.name }).containsExactly("notes.md", "new.txt")
    }
    @Test fun aFolderInTheWay_isAConflictThatCannotBeReplaced() {
        val archive = index("DCIM", "notes.md")
        val folderPlan = ExtractionPlan.of(archive.entries)
        val withFolder = mapOf(emptyList<String>() to ExistingNames(files = setOf("notes.md"), folders = setOf("dcim")))
        val conflicts = ConflictPlan.conflicts(folderPlan, withFolder).associateBy { it.file.name }
        assertThat(conflicts.getValue("DCIM").replaceable).isFalse()
        assertThat(conflicts.getValue("notes.md").replaceable).isTrue()
        val resolved = ConflictPlan.resolve(folderPlan, withFolder, folderPlan.files.associateWith { ConflictChoice.REPLACE })
            .associateBy { it.file.name }
        assertThat(resolved.getValue("DCIM").replace).isFalse()
        assertThat(resolved.getValue("DCIM").targetName).isEqualTo("DCIM (1)")
        assertThat(resolved.getValue("notes.md").replace).isTrue()
    }
}
