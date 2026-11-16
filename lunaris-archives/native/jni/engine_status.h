/* SPDX-License-Identifier: GPL-3.0-or-later
 * Codes shared with Kotlin: EngineStatus, EntryOutcome and the answers of NativeSink
 * (a JVM test compares the three lists with this header). */
#ifndef LUNARIS_ENGINE_STATUS_H
#define LUNARIS_ENGINE_STATUS_H

#define ENGINE_OK 0
#define ENGINE_NOT_ARCHIVE 1
#define ENGINE_DAMAGED 2
#define ENGINE_TRUNCATED 3
#define ENGINE_HEADERS_ENCRYPTED 4
#define ENGINE_DATA_ENCRYPTED 5
#define ENGINE_WRONG_PASSWORD 6
#define ENGINE_RAR4_ENCRYPTED 7
#define ENGINE_DICTIONARY_TOO_LARGE 8
#define ENGINE_NEEDS_NEXT_VOLUME 9
#define ENGINE_UNSUPPORTED_FEATURE 10
#define ENGINE_CANCELLED 11
#define ENGINE_LIMIT_REACHED 12
#define ENGINE_OUT_OF_MEMORY 13
#define ENGINE_ACCESS_DENIED 14
#define ENGINE_PATH_MISMATCH 15

#define ENTRY_OK 0
#define ENTRY_WRONG_PASSWORD 1
#define ENTRY_DAMAGED 2
#define ENTRY_UNSUPPORTED 3
#define ENTRY_LIMIT 4
#define ENTRY_ENCRYPTED 5
#define ENTRY_CANCELLED 6

/* What NativeSink.onEntryStart and NativeSink.onData answer. */
#define SINK_CONTINUE 0
#define SINK_LIMIT 1     /* this entry grew past its declared size: stop the entry, go on with the next */
#define SINK_MISMATCH 2  /* the entry at this index is not the one that was asked for */
#define SINK_STOP 3      /* the pipe is closed or the sink failed: stop the whole job */

#endif
