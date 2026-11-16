package io.github.martindavinci.lunaris.archives.safety

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.safety.SafePath.Result.Ok
import io.github.martindavinci.lunaris.archives.safety.SafePath.Result.Skipped
import org.junit.Test

class SafePathTest {
    private fun outside(name: String) = assertThat(SafePath.check(name)).isEqualTo(Skipped(SkipReason.OUTSIDE_FOLDER))
    private fun invalid(name: String) = assertThat(SafePath.check(name)).isEqualTo(Skipped(SkipReason.INVALID_NAME))

    @Test fun plainPath_keepsItsComponents() {
        assertThat(SafePath.check("docs/notes.md")).isEqualTo(Ok(listOf("docs", "notes.md")))
    }
    @Test fun emptyAndDotComponents_areDropped() {
        assertThat(SafePath.check("./docs//./notes.md")).isEqualTo(Ok(listOf("docs", "notes.md")))
        assertThat(SafePath.check("docs/")).isEqualTo(Ok(listOf("docs")))
    }
    @Test fun backslashes_becomeSlashesBeforeAnyCheck() {
        assertThat(SafePath.check("docs\\notes.md")).isEqualTo(Ok(listOf("docs", "notes.md")))
        outside("\\..\\x"); outside("..\\..\\x")
    }
    @Test fun parentComponents_areRefusedNotNormalised() {
        outside("../x"); outside("a/../../x"); outside("a/../b"); outside("..")
    }
    @Test fun absoluteAndUncPaths_areOutside() { outside("/etc/hosts"); outside("//server/x"); outside("//") }
    @Test fun driveLetters_areOutside() { outside("C:\\x"); outside("C:x"); outside("c:/x") }
    @Test fun nul_isInvalid() { invalid("evil\u0000.txt") }
    @Test fun nothingLeft_isInvalid() { invalid(""); invalid("./") }
    @Test fun tooDeep_isInvalid() {
        invalid(List(Limits.MAX_PATH_DEPTH + 1) { "a" }.joinToString("/"))
        assertThat(SafePath.check(List(Limits.MAX_PATH_DEPTH) { "a" }.joinToString("/"))).isInstanceOf(Ok::class.java)
    }
    @Test fun tooLong_isInvalid() { invalid("a".repeat(Limits.MAX_PATH_BYTES + 1)) }
    @Test fun dotsOnlyName_isNotParent() { assertThat(SafePath.check("...")).isEqualTo(Ok(listOf("..."))) }
    @Test fun links_areSkippedAsLinks() {
        assertThat(SafePath.evaluate("docs/a", EntryType.SYMLINK)).isEqualTo(Skipped(SkipReason.LINK))
        assertThat(SafePath.evaluate("docs/a", EntryType.HARDLINK)).isEqualTo(Skipped(SkipReason.LINK))
    }
    @Test fun devices_areSkippedAsDeviceFiles() {
        listOf(EntryType.CHAR_DEVICE, EntryType.BLOCK_DEVICE, EntryType.FIFO, EntryType.SOCKET).forEach {
            assertThat(SafePath.evaluate("dev/x", it)).isEqualTo(Skipped(SkipReason.DEVICE_FILE))
        }
    }
    @Test fun typeRuleWins_overPathRule() {
        assertThat(SafePath.evaluate("../x", EntryType.SYMLINK)).isEqualTo(Skipped(SkipReason.LINK))
    }
    @Test fun filesAndFolders_followThePathRules() {
        assertThat(SafePath.evaluate("docs/a", EntryType.FILE)).isEqualTo(Ok(listOf("docs", "a")))
        assertThat(SafePath.evaluate("../a", EntryType.DIRECTORY)).isEqualTo(Skipped(SkipReason.OUTSIDE_FOLDER))
    }
}
