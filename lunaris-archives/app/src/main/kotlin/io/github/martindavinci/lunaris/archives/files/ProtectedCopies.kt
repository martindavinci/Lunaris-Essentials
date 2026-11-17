package io.github.martindavinci.lunaris.archives.files

import java.io.File

/** Copies another app may still be writing: no clean-up of the cache may delete them. */
fun interface ProtectedCopies {
    fun files(): Set<File>

    companion object {
        val NONE = ProtectedCopies { emptySet() }
    }
}

/** Ends what another app was given on a copy (on the device: Context.revokeUriPermission). */
fun interface UriRevoker {
    fun revoke(file: File)
}

/** True when [dir] is a protected file or holds one, at any depth. */
internal fun ProtectedCopies.guards(dir: File, protected: Set<File> = files()): Boolean =
    protected.any { it == dir || it.startsWith(dir) }
