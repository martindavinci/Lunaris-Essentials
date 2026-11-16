package io.github.martindavinci.lunaris.archives.extract

import java.util.Locale

enum class ConflictChoice { REPLACE, KEEP_BOTH, SKIP }

/**
 * [replaceable] is false when what is in the way is a folder: the dialog then offers only
 * "Keep both" and "Skip", and [ConflictPlan.resolve] never answers "replace" for it.
 */
data class Conflict(val file: PlannedFile, val existingName: String, val replaceable: Boolean = true)

data class ResolvedFile(val file: PlannedFile, val targetName: String, val replace: Boolean)

/** Name conflicts, found and settled before anything is written (spec "Conflitti"). */
object ConflictPlan {
    fun conflicts(plan: ExtractionPlan, existing: Map<List<String>, ExistingNames>): List<Conflict> {
        val planned = HashMap<List<String>, MutableMap<String, String>>()   // folder -> lower-cased name -> first name planned
        val found = ArrayList<Conflict>()
        for (file in plan.files) {
            val key = file.name.lowercase(Locale.ROOT)
            val here = existing[file.folder]
            val earlier = planned.getOrPut(file.folder) { HashMap() }
            when {
                here != null && key in here.folders -> found += Conflict(file, file.name, replaceable = false)
                here != null && key in here.files -> found += Conflict(file, file.name)
                // Two entries of the archive with the same final name: the second meets the first.
                key in earlier -> found += Conflict(file, earlier.getValue(key))
            }
            earlier.putIfAbsent(key, file.name)
        }
        return found
    }

    /** Files to write, in plan order, each with its final name. A conflict without a choice keeps both. */
    fun resolve(
        plan: ExtractionPlan,
        existing: Map<List<String>, ExistingNames>,
        choices: Map<PlannedFile, ConflictChoice>,
    ): List<ResolvedFile> {
        val conflicts = conflicts(plan, existing).associateBy { it.file }
        val taken = HashMap<List<String>, MutableSet<String>>()
        return plan.files.mapNotNull { file ->
            val names = taken.getOrPut(file.folder) { existing[file.folder]?.all?.toMutableSet() ?: HashSet() }
            val conflict = conflicts[file]
            val choice = choices[file] ?: ConflictChoice.KEEP_BOTH
            val resolved = when {
                conflict == null -> ResolvedFile(file, file.name, replace = false)
                choice == ConflictChoice.SKIP -> null
                choice == ConflictChoice.REPLACE && conflict.replaceable -> ResolvedFile(file, file.name, replace = true)
                else -> ResolvedFile(file, KeepBothNamer.name(file.name, names), replace = false)
            }
            resolved?.also { names += it.targetName.lowercase(Locale.ROOT) }
        }
    }
}
