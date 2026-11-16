/* SPDX-License-Identifier: GPL-3.0-or-later */
#ifndef LUNARIS_ENGINE_INTERNAL_H
#define LUNARIS_ENGINE_INTERNAL_H

#include <jni.h>
#include <stdatomic.h>
#include <stdint.h>
#include <archive.h>
#include <archive_entry.h>
#include "engine_status.h"

#define ENGINE_CHUNK_BYTES (64 * 1024)
#define ENGINE_MAX_VOLUMES 64
/* While an extraction skips over entries it does not want, say how far it is this often. */
#define ENGINE_PROGRESS_STEP_BYTES (4 * 1024 * 1024)

typedef struct { atomic_int cancelled; } engine_job;

typedef struct {
    JNIEnv *env;
    jobject sink;
    uint8_t *out;      /* NativeSink.buffer, direct, ENGINE_CHUNK_BYTES */
} engine_sink;

typedef struct {
    int fd;            /* owned by the Java ParcelFileDescriptor */
    int64_t size;
    int64_t pos;       /* own offset: pread never moves the fd's shared offset */
    int64_t reported;  /* last offset sent as progress */
    engine_job *job;
    engine_sink *progress;  /* extraction only; NULL while listing */
    uint8_t buf[ENGINE_CHUNK_BYTES];
} engine_volume;

/* engine_read.c */
void engine_register(struct archive *a);
struct archive *engine_open(engine_volume *vols, int count, int *status);
int engine_is_raw_without_filter(struct archive *a);
int engine_cancelled(const engine_job *job);

/* engine_sink.c: JNI upcalls. Every one of them does nothing once a Java exception is pending,
 * so the callers may keep their error paths simple. "go on" answers are 1 = go on, 0 = stop. */
int sink_bind(JNIEnv *env);
int sink_init(engine_sink *s, JNIEnv *env, jobject sink);
int sink_entry(engine_sink *s, int64_t index, struct archive_entry *e);
int sink_entry_start(engine_sink *s, int64_t index, struct archive_entry *e, int64_t archive_bytes); /* SINK_* */
int sink_data(engine_sink *s, int length, int64_t archive_bytes);                                    /* SINK_* */
int sink_entry_end(engine_sink *s, int64_t index, int outcome);
int sink_progress(engine_sink *s, int64_t archive_bytes);
void sink_archive(engine_sink *s, struct archive *a);
void sink_error(engine_sink *s, struct archive *a);

/* engine_list.c, engine_extract.c */
int engine_list(engine_sink *s, engine_job *job, engine_volume *vols, int count, int max_entries);
int engine_extract(engine_sink *s, engine_job *job, engine_volume *vols, int count, const int64_t *wanted, int n);

#endif
