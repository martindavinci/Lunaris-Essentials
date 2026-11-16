package io.github.martindavinci.lunaris.archives.testing

import java.io.File

/** Committed test archives in src/test/resources/fixtures (made by tools/fixtures/make_fixtures.py). */
object Fixtures {
    private const val DIR = "src/test/resources/fixtures"
    fun file(name: String): File = File(DIR, name).also { require(it.isFile) { "missing fixture $name" } }
    fun bytes(name: String): ByteArray = file(name).readBytes()
}
