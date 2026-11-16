package io.github.martindavinci.lunaris.archives.archive

/** What an entry is; [code] travels on the wire. */
enum class EntryType(val code: Int) {
    FILE(0), DIRECTORY(1), SYMLINK(2), HARDLINK(3), CHAR_DEVICE(4), BLOCK_DEVICE(5), FIFO(6), SOCKET(7);

    companion object {
        fun of(code: Int): EntryType = entries.first { it.code == code }
    }
}
