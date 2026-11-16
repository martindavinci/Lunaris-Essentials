/* SPDX-License-Identifier: GPL-3.0-or-later
 * One pass in archive order: skip to each wanted index, stream its bytes through the sink. */
#include <string.h>
#include "engine_internal.h"

/* Sends n bytes from p (zeros when p is NULL, for the holes of sparse files) in chunks. */
static int emit(engine_sink *s, struct archive *a, const uint8_t *p, int64_t n) {
    while (n > 0) {
        int c = n < ENGINE_CHUNK_BYTES ? (int) n : ENGINE_CHUNK_BYTES;
        if (p != NULL) memcpy(s->out, p, (size_t) c); else memset(s->out, 0, (size_t) c);
        int answer = sink_data(s, c, archive_filter_bytes(a, -1));
        if (answer != SINK_CONTINUE) return answer;
        if (p != NULL) p += c;
        n -= c;
    }
    return SINK_CONTINUE;
}

static int copy_entry(engine_sink *s, engine_job *job, struct archive *a) {
    const void *buf;
    size_t len;
    la_int64_t offset;
    int64_t written = 0;
    int r;
    while ((r = archive_read_data_block(a, &buf, &len, &offset)) == ARCHIVE_OK) {
        if (engine_cancelled(job)) return ENTRY_CANCELLED;
        int answer = SINK_CONTINUE;
        if (offset > written) {            /* sparse hole: the sink's size limit bounds it */
            answer = emit(s, a, NULL, offset - written);
            written = offset;
        }
        if (answer == SINK_CONTINUE) {
            answer = emit(s, a, buf, (int64_t) len);
            written += (int64_t) len;
        }
        if (answer == SINK_LIMIT) return ENTRY_LIMIT;
        if (answer != SINK_CONTINUE) return ENTRY_CANCELLED;   /* pipe closed or sink failed */
    }
    if (r == ARCHIVE_EOF) return ENTRY_OK;
    sink_error(s, a);
    return engine_cancelled(job) ? ENTRY_CANCELLED : ENTRY_DAMAGED;
}

/* wanted[] must be strictly increasing (LibarchiveReader sorts and removes duplicates). */
int engine_extract(engine_sink *s, engine_job *job, engine_volume *vols, int count, const int64_t *wanted, int n) {
    int status;
    for (int i = 0; i < count; i++) vols[i].progress = s;
    struct archive *a = engine_open(vols, count, &status);
    if (a == NULL) return status;
    if (status != ENGINE_OK) { sink_error(s, a); archive_read_free(a); return status; }
    struct archive_entry *e;
    int64_t index = 0;
    int next = 0, r = ARCHIVE_OK;
    while (next < n && ((r = archive_read_next_header(a, &e)) == ARCHIVE_OK || r == ARCHIVE_WARN)) {
        if (engine_cancelled(job)) { status = ENGINE_CANCELLED; break; }
        if (index == wanted[next]) {
            next++;
            int start = sink_entry_start(s, index, e, archive_filter_bytes(a, -1));
            if (start != SINK_CONTINUE) {
                status = start == SINK_MISMATCH ? ENGINE_PATH_MISMATCH : ENGINE_CANCELLED;
                break;
            }
            /* Encrypted data is never read here: the main process sends it to a Kotlin engine. */
            int outcome = archive_entry_is_data_encrypted(e) > 0 ? ENTRY_ENCRYPTED : copy_entry(s, job, a);
            if (!sink_entry_end(s, index, outcome) || outcome == ENTRY_CANCELLED) {
                status = ENGINE_CANCELLED;
                break;
            }
            /* A damaged entry does not end the pass: in a ZIP the next one may be fine. In a
             * solid archive the next header call fails and ends the loop. */
        }
        index++;
    }
    if (status == ENGINE_OK && next < n) {
        if (r == ARCHIVE_EOF) {
            status = ENGINE_PATH_MISMATCH;   /* fewer entries than the index said: the file changed */
        } else {
            sink_error(s, a);
            status = engine_cancelled(job) ? ENGINE_CANCELLED : ENGINE_DAMAGED;
        }
    }
    archive_read_free(a);
    return status;
}
