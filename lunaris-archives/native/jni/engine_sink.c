/* SPDX-License-Identifier: GPL-3.0-or-later
 * JNI upcalls into io.github.martindavinci.lunaris.archives.engine.NativeSink.
 *
 * Rule of this file: with a Java exception pending, only ExceptionCheck and DeleteLocalRef are
 * called. Every function checks first and turns into a no-op, so a failed upcall can never be
 * followed by another JNI call. The exception itself is thrown in Kotlin when the native
 * method returns. */
#include <string.h>
#include "engine_internal.h"

#define SINK_CLASS "io/github/martindavinci/lunaris/archives/engine/NativeSink"

static jmethodID on_archive, on_entry, on_entry_start, on_data, on_entry_end, on_progress, on_error;
static jfieldID buffer_field;

static int failed(JNIEnv *env) { return (*env)->ExceptionCheck(env) == JNI_TRUE; }

static jmethodID method(JNIEnv *env, jclass c, const char *name, const char *signature) {
    return failed(env) ? NULL : (*env)->GetMethodID(env, c, name, signature);
}

int sink_bind(JNIEnv *env) {
    jclass c = (*env)->FindClass(env, SINK_CLASS);
    if (c == NULL) return -1;
    on_archive = method(env, c, "onArchive", "(I[II)V");
    on_entry = method(env, c, "onEntry", "(J[BZIJZJZZ[BZ)Z");
    on_entry_start = method(env, c, "onEntryStart", "(J[BJZJ)I");
    on_data = method(env, c, "onData", "(IJ)I");
    on_entry_end = method(env, c, "onEntryEnd", "(JI)Z");
    on_progress = method(env, c, "onProgress", "(J)Z");
    on_error = method(env, c, "onError", "(I[B)V");
    buffer_field = failed(env) ? NULL : (*env)->GetFieldID(env, c, "buffer", "Ljava/nio/ByteBuffer;");
    (*env)->DeleteLocalRef(env, c);
    return (buffer_field != NULL && !failed(env)) ? 0 : -1;
}

int sink_init(engine_sink *s, JNIEnv *env, jobject sink) {
    s->env = env;
    s->sink = sink;
    s->out = NULL;
    jobject buffer = (*env)->GetObjectField(env, sink, buffer_field);
    if (buffer == NULL) return -1;
    if ((*env)->GetDirectBufferCapacity(env, buffer) >= ENGINE_CHUNK_BYTES) {
        s->out = (*env)->GetDirectBufferAddress(env, buffer);
    }
    (*env)->DeleteLocalRef(env, buffer);
    return s->out != NULL ? 0 : -1;
}

/* The bytes of a C string as a byte[]; NULL (with an exception pending) when memory is short. */
static jbyteArray bytes(JNIEnv *env, const char *text) {
    jsize n = (jsize) strlen(text);
    jbyteArray array = (*env)->NewByteArray(env, n);
    if (array != NULL && n > 0) (*env)->SetByteArrayRegion(env, array, 0, n, (const jbyte *) text);
    return array;
}

/* UTF-8 when libarchive can give it; otherwise the raw bytes, for Kotlin to decode (CP437 in old ZIPs). */
static const char *path_of(struct archive_entry *e, jboolean *utf8) {
    const char *p = archive_entry_pathname_utf8(e);
    *utf8 = p != NULL ? JNI_TRUE : JNI_FALSE;
    if (p == NULL) p = archive_entry_pathname(e);
    return p != NULL ? p : "";   /* an entry without a name: Kotlin skips it as an invalid name */
}

int sink_entry(engine_sink *s, int64_t index, struct archive_entry *e) {
    JNIEnv *env = s->env;
    if (failed(env)) return 0;
    jboolean utf8;
    jbyteArray path = bytes(env, path_of(e, &utf8));
    const char *hard = archive_entry_hardlink(e);
    const char *link = hard != NULL ? hard : archive_entry_symlink(e);
    jbyteArray target = (link != NULL && !failed(env)) ? bytes(env, link) : NULL;
    jboolean go = JNI_FALSE;
    if (!failed(env)) {
        go = (*env)->CallBooleanMethod(env, s->sink, on_entry, (jlong) index, path, utf8,
            (jint) archive_entry_filetype(e), (jlong) archive_entry_size(e),
            (jboolean) (archive_entry_size_is_set(e) != 0),
            (jlong) archive_entry_mtime(e), (jboolean) (archive_entry_mtime_is_set(e) != 0),
            (jboolean) (archive_entry_is_data_encrypted(e) > 0 || archive_entry_is_metadata_encrypted(e) > 0),
            target, (jboolean) (hard != NULL));
    }
    if (path != NULL) (*env)->DeleteLocalRef(env, path);
    if (target != NULL) (*env)->DeleteLocalRef(env, target);
    return !failed(env) && go == JNI_TRUE;
}

int sink_entry_start(engine_sink *s, int64_t index, struct archive_entry *e, int64_t archive_bytes) {
    JNIEnv *env = s->env;
    if (failed(env)) return SINK_STOP;
    jboolean utf8;
    jbyteArray path = bytes(env, path_of(e, &utf8));
    jint answer = SINK_STOP;
    if (!failed(env)) {
        answer = (*env)->CallIntMethod(env, s->sink, on_entry_start, (jlong) index, path,
            (jlong) archive_entry_size(e), (jboolean) (archive_entry_size_is_set(e) != 0), (jlong) archive_bytes);
    }
    if (path != NULL) (*env)->DeleteLocalRef(env, path);
    return failed(env) ? SINK_STOP : (int) answer;
}

int sink_data(engine_sink *s, int length, int64_t archive_bytes) {
    JNIEnv *env = s->env;
    if (failed(env)) return SINK_STOP;
    jint answer = (*env)->CallIntMethod(env, s->sink, on_data, (jint) length, (jlong) archive_bytes);
    return failed(env) ? SINK_STOP : (int) answer;
}

int sink_entry_end(engine_sink *s, int64_t index, int outcome) {
    JNIEnv *env = s->env;
    if (failed(env)) return 0;
    jboolean go = (*env)->CallBooleanMethod(env, s->sink, on_entry_end, (jlong) index, (jint) outcome);
    return !failed(env) && go == JNI_TRUE;
}

int sink_progress(engine_sink *s, int64_t archive_bytes) {
    JNIEnv *env = s->env;
    if (failed(env)) return 0;
    jboolean go = (*env)->CallBooleanMethod(env, s->sink, on_progress, (jlong) archive_bytes);
    return !failed(env) && go == JNI_TRUE;
}

void sink_archive(engine_sink *s, struct archive *a) {
    JNIEnv *env = s->env;
    if (failed(env)) return;
    int count = archive_filter_count(a);
    if (count < 0) count = 0;
    jintArray filters = (*env)->NewIntArray(env, count);
    if (filters == NULL) return;
    for (int i = 0; i < count; i++) {
        jint code = archive_filter_code(a, i);
        (*env)->SetIntArrayRegion(env, filters, i, 1, &code);
    }
    (*env)->CallVoidMethod(env, s->sink, on_archive, (jint) archive_format(a), filters,
        (jint) archive_read_has_encrypted_entries(a));
    (*env)->DeleteLocalRef(env, filters);
}

/* The message travels as bytes: it can quote an entry name, which need not be valid UTF-8,
 * and NewStringUTF aborts on such input. */
void sink_error(engine_sink *s, struct archive *a) {
    JNIEnv *env = s->env;
    if (failed(env)) return;
    const char *text = archive_error_string(a);
    jbyteArray message = text != NULL ? bytes(env, text) : NULL;
    if (!failed(env)) (*env)->CallVoidMethod(env, s->sink, on_error, (jint) archive_errno(a), message);
    if (message != NULL) (*env)->DeleteLocalRef(env, message);
}
