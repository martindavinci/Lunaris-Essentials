package tools.lunaris.texteditor.file

import java.io.File

/** True when [file] resolves (after `..` and symlinks) inside one of [roots]. */
fun isInsideAny(file: File, roots: List<File>): Boolean {
    val path = file.canonicalFile.toPath()
    return roots.any { path.startsWith(it.canonicalFile.toPath()) }
}
