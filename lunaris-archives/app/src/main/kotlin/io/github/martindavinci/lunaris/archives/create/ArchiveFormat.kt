package io.github.martindavinci.lunaris.archives.create

/** What a new archive is written as: its file suffix, its MIME type, and which options apply. */
enum class ArchiveFormat(val suffix: String, val mime: String, val takesPassword: Boolean, val usesLevel: Boolean) {
    ZIP(".zip", "application/zip", takesPassword = true, usesLevel = true),
    SEVEN_Z(".7z", "application/x-7z-compressed", takesPassword = false, usesLevel = true),
    TAR(".tar", "application/x-tar", takesPassword = false, usesLevel = false),
    TAR_GZ(".tar.gz", "application/gzip", takesPassword = false, usesLevel = false),
    TAR_XZ(".tar.xz", "application/x-xz", takesPassword = false, usesLevel = false),
    TAR_BZ2(".tar.bz2", "application/x-bzip2", takesPassword = false, usesLevel = false);

    val isTar: Boolean get() = this == TAR || this == TAR_GZ || this == TAR_XZ || this == TAR_BZ2
}

/** The first choice on the New archive screen. */
enum class FormatChoice { ZIP, SEVEN_Z, TAR }

/** How a TAR is packed: the second choice, shown only for TAR. */
enum class Packing { NONE, GZ, XZ, BZ2 }

fun archiveFormat(choice: FormatChoice, packing: Packing): ArchiveFormat = when (choice) {
    FormatChoice.ZIP -> ArchiveFormat.ZIP
    FormatChoice.SEVEN_Z -> ArchiveFormat.SEVEN_Z
    FormatChoice.TAR -> when (packing) {
        Packing.NONE -> ArchiveFormat.TAR
        Packing.GZ -> ArchiveFormat.TAR_GZ
        Packing.XZ -> ArchiveFormat.TAR_XZ
        Packing.BZ2 -> ArchiveFormat.TAR_BZ2
    }
}
