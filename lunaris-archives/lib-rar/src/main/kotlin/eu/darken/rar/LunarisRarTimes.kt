// SPDX-License-Identifier: GPL-3.0-or-later
// Added by Lunaris Archives: the app reads times through this, so it never touches kotlin.time.Instant.
package eu.darken.rar

val RarEntry.mtimeMillis: Long?
    get() = mtime?.toEpochMilliseconds()
