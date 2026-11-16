// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.martindavinci.lunaris.archives.engine;

interface IArchiveEngine {
    int protocolVersion();
    oneway void list(long jobId, in ParcelFileDescriptor[] volumes, int route, in char[] password, in ParcelFileDescriptor sink);
    oneway void extract(long jobId, in ParcelFileDescriptor[] volumes, int route, in char[] password, in long[] indices, in ParcelFileDescriptor sink);
    oneway void cancel(long jobId);
}
