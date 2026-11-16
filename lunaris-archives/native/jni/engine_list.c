/* SPDX-License-Identifier: GPL-3.0-or-later
 * Walks the headers of an archive and hands each one to the sink. */
#include "engine_internal.h"

int engine_list(engine_sink *s, engine_job *job, engine_volume *vols, int count, int max_entries) {
    int status;
    struct archive *a = engine_open(vols, count, &status);
    if (a == NULL) return status;
    if (status != ENGINE_OK) { sink_error(s, a); archive_read_free(a); return status; }
    struct archive_entry *e;
    int64_t index = 0;
    int r;
    while ((r = archive_read_next_header(a, &e)) == ARCHIVE_OK || r == ARCHIVE_WARN) {
        if (index == 0) {
            if (engine_is_raw_without_filter(a)) { status = ENGINE_NOT_ARCHIVE; break; }
            /* Format and filters are known from the first header on: the sink uses them to
             * decide whether it can pass entries on at once (everything but ZIP). */
            sink_archive(s, a);
        }
        if (engine_cancelled(job)) { status = ENGINE_CANCELLED; break; }
        if (index >= max_entries) { status = ENGINE_LIMIT_REACHED; break; }
        if (!sink_entry(s, index, e)) { status = ENGINE_CANCELLED; break; }
        index++;
    }
    if (status == ENGINE_OK && r != ARCHIVE_EOF) {
        /* ENGINE_DAMAGED means "see the error given to onError": Kotlin classifies it. */
        sink_error(s, a);
        status = engine_cancelled(job) ? ENGINE_CANCELLED : ENGINE_DAMAGED;
    }
    sink_archive(s, a);   /* again at the end: only now is "has encrypted entries" complete */
    archive_read_free(a);
    return status;
}
