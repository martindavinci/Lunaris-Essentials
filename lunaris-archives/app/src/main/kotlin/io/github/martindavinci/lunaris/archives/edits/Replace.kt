package io.github.martindavinci.lunaris.archives.edits

import io.github.martindavinci.lunaris.archives.safety.Limits
import java.io.File
import java.io.RandomAccessFile

/** Writes a whole file over the archive and checks that the archive now holds exactly those bytes. */
internal object Replace {
    /** True when the archive reads back with the size and fingerprint of [source]. Throws what the provider threw. */
    fun copy(source: File, uri: String, io: UpdateIo): Boolean {
        io.openForReplace(uri).use { target ->
            source.inputStream().use { it.copyTo(target.stream, Limits.WIRE_CHUNK_BYTES) }
            target.stream.flush()
            target.truncateHere()
            target.sync()
        }
        val now = io.facts(uri) ?: return false
        return now.size == source.length() && now.fingerprint == fingerprint(source)
    }

    fun fingerprint(file: File): String = RandomAccessFile(file, "r").use { Fingerprint.of(it.channel, file.length()) }
}
