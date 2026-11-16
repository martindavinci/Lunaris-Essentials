/* SPDX-License-Identifier: GPL-3.0-or-later
 * Archive input over pread64 with our own offset, so the descriptor's shared offset never moves. */
#include <errno.h>
#include <stdio.h>
#include <unistd.h>
#include "engine_internal.h"

int engine_cancelled(const engine_job *job) { return atomic_load(&job->cancelled) != 0; }

/* Exactly the readers the app promises. Never the *_all() calls: they add program filters and mtree. */
void engine_register(struct archive *a) {
    archive_read_support_format_zip(a);
    archive_read_support_format_7zip(a);
    archive_read_support_format_rar(a);
    archive_read_support_format_rar5(a);
    archive_read_support_format_tar(a);
    archive_read_support_format_cpio(a);
    archive_read_support_format_iso9660(a);
    archive_read_support_format_empty(a);
    archive_read_support_format_raw(a);
    archive_read_support_filter_gzip(a);
    archive_read_support_filter_bzip2(a);
    archive_read_support_filter_xz(a);
    archive_read_support_filter_zstd(a);
    archive_read_support_filter_lz4(a);
    /* The seekable ZIP reader folds __MACOSX/._* entries into their files by default; the Kotlin
     * readers (ZipDirectory) list every entry, and an index must mean the same entry in both. */
    archive_read_set_format_option(a, "zip", "mac-ext", NULL);
}

static la_ssize_t stop_cancelled(struct archive *a) {
    archive_set_error(a, ECANCELED, "cancelled");
    return -1;
}

static la_ssize_t vol_read(struct archive *a, void *data, const void **out) {
    engine_volume *v = data;
    *out = v->buf;
    if (engine_cancelled(v->job)) return stop_cancelled(a);
    if (v->pos >= v->size) return 0;
    ssize_t n;
    do { n = pread64(v->fd, v->buf, sizeof v->buf, (off64_t) v->pos); } while (n < 0 && errno == EINTR);
    if (n < 0) { archive_set_error(a, errno, "read failed"); return -1; }
    v->pos += n;
    /* A solid archive can read for a long time before the entry that was asked for: keep the
     * main process informed, and notice early when it has gone away. */
    if (v->progress != NULL && v->pos - v->reported >= ENGINE_PROGRESS_STEP_BYTES) {
        v->reported = v->pos;
        if (!sink_progress(v->progress, v->pos)) {
            atomic_store(&v->job->cancelled, 1);
            return stop_cancelled(a);
        }
    }
    return n;
}

static la_int64_t vol_seek(struct archive *a, void *data, la_int64_t offset, int whence) {
    engine_volume *v = data;
    int64_t base;
    switch (whence) {
        case SEEK_SET: base = 0; break;
        case SEEK_CUR: base = v->pos; break;
        case SEEK_END: base = v->size; break;
        default: archive_set_error(a, EINVAL, "bad seek"); return ARCHIVE_FATAL;
    }
    if ((offset > 0 && base > INT64_MAX - offset) || (offset < 0 && base + offset < 0)) {
        archive_set_error(a, EINVAL, "seek out of range");
        return ARCHIVE_FATAL;
    }
    v->pos = base + offset;
    v->reported = v->pos;
    return v->pos;
}

static la_int64_t vol_skip(struct archive *a, void *data, la_int64_t request) {
    (void) a;
    engine_volume *v = data;
    int64_t left = v->size > v->pos ? v->size - v->pos : 0;
    int64_t n = request < left ? request : left;
    if (n < 0) n = 0;
    v->pos += n;
    return n;
}

static int vol_switch(struct archive *a, void *from, void *to) {
    (void) a; (void) from;
    if (to != NULL) ((engine_volume *) to)->pos = 0;
    return ARCHIVE_OK;
}

static int vol_noop(struct archive *a, void *data) { (void) a; (void) data; return ARCHIVE_OK; }

struct archive *engine_open(engine_volume *vols, int count, int *status) {
    struct archive *a = archive_read_new();
    if (a == NULL) { *status = ENGINE_OUT_OF_MEMORY; return NULL; }
    engine_register(a);
    archive_read_set_open_callback(a, vol_noop);
    archive_read_set_read_callback(a, vol_read);
    archive_read_set_seek_callback(a, vol_seek);
    archive_read_set_skip_callback(a, vol_skip);
    archive_read_set_switch_callback(a, vol_switch);
    archive_read_set_close_callback(a, vol_noop);
    archive_read_set_callback_data(a, &vols[0]);
    for (int i = 1; i < count; i++) archive_read_append_callback_data(a, &vols[i]);
    /* ENGINE_DAMAGED here means "see archive_error_string": the Kotlin side classifies it. */
    *status = archive_read_open1(a) == ARCHIVE_OK ? ENGINE_OK : ENGINE_DAMAGED;
    return a;
}

int engine_is_raw_without_filter(struct archive *a) {
    /* raw accepts anything: a raw "archive" with no compression filter is not an archive. */
    return (archive_format(a) & ARCHIVE_FORMAT_BASE_MASK) == ARCHIVE_FORMAT_RAW && archive_filter_count(a) <= 1;
}
