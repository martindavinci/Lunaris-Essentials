package io.github.martindavinci.lunaris.pdf.viewer

import java.io.InputStream

/** The bytes of the document being read. */
interface DocumentSource {
    /** The size in bytes, or null when the provider does not say. */
    fun size(): Long?

    /** A stream positioned at [start]. May throw when the file is gone or no longer allowed. */
    fun open(start: Long): InputStream
}
