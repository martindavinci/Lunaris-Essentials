/* SPDX-License-Identifier: GPL-3.0-or-later
 * JNI entry points of io.github.martindavinci.lunaris.archives.engine.NativeEngine. */
#include <errno.h>
#include <locale.h>
#include <signal.h>
#include <stdlib.h>
#include <sys/stat.h>
#include "engine_internal.h"

#define ENGINE_CLASS "io/github/martindavinci/lunaris/archives/engine/NativeEngine"
#define SINK_TYPE "Lio/github/martindavinci/lunaris/archives/engine/NativeSink;"

static engine_job *job_of(jlong handle) { return (engine_job *) (intptr_t) handle; }

static jlong new_job(JNIEnv *env, jclass clazz) {
    (void) env; (void) clazz;
    engine_job *job = calloc(1, sizeof *job);
    if (job != NULL) atomic_init(&job->cancelled, 0);
    return (jlong) (intptr_t) job;   /* 0 = out of memory */
}

/* From any thread. EngineJob (Kotlin) guarantees it never runs after free_job. */
static void cancel_job(JNIEnv *env, jclass clazz, jlong handle) {
    (void) env; (void) clazz;
    engine_job *job = job_of(handle);
    if (job != NULL) atomic_store(&job->cancelled, 1);
}

static void free_job(JNIEnv *env, jclass clazz, jlong handle) {
    (void) env; (void) clazz;
    free(job_of(handle));
}

/* One engine_volume per descriptor. NULL with *status set when that cannot be done. */
static engine_volume *open_volumes(JNIEnv *env, jintArray fds, engine_job *job, int *count, int *status) {
    jsize n = fds != NULL ? (*env)->GetArrayLength(env, fds) : 0;
    if (n < 1 || n > ENGINE_MAX_VOLUMES) { *status = ENGINE_UNSUPPORTED_FEATURE; return NULL; }
    jint raw[ENGINE_MAX_VOLUMES];
    (*env)->GetIntArrayRegion(env, fds, 0, n, raw);
    engine_volume *vols = calloc((size_t) n, sizeof *vols);
    if (vols == NULL) { *status = ENGINE_OUT_OF_MEMORY; return NULL; }
    for (jsize i = 0; i < n; i++) {
        struct stat st;
        if (fstat(raw[i], &st) != 0) {
            *status = (errno == EACCES || errno == EPERM) ? ENGINE_ACCESS_DENIED : ENGINE_DAMAGED;
            free(vols);
            return NULL;
        }
        vols[i].fd = raw[i];
        vols[i].size = (int64_t) st.st_size;
        vols[i].job = job;
    }
    *count = (int) n;
    *status = ENGINE_OK;
    return vols;
}

static jint list(JNIEnv *env, jclass clazz, jlong handle, jintArray fds, jint max_entries, jobject sink) {
    (void) clazz;
    engine_job *job = job_of(handle);
    if (job == NULL || sink == NULL) return ENGINE_OUT_OF_MEMORY;
    int count = 0, status = ENGINE_OK;
    engine_volume *vols = open_volumes(env, fds, job, &count, &status);
    if (vols == NULL) return status;
    engine_sink s;
    status = sink_init(&s, env, sink) == 0 ? engine_list(&s, job, vols, count, max_entries) : ENGINE_OUT_OF_MEMORY;
    free(vols);
    return status;
}

static jint extract(JNIEnv *env, jclass clazz, jlong handle, jintArray fds, jlongArray indices, jobject sink) {
    (void) clazz;
    engine_job *job = job_of(handle);
    if (job == NULL || sink == NULL) return ENGINE_OUT_OF_MEMORY;
    jsize n = indices != NULL ? (*env)->GetArrayLength(env, indices) : 0;
    if (n == 0) return ENGINE_OK;
    int64_t *wanted = malloc((size_t) n * sizeof *wanted);
    if (wanted == NULL) return ENGINE_OUT_OF_MEMORY;
    (*env)->GetLongArrayRegion(env, indices, 0, n, (jlong *) wanted);
    int count = 0, status = ENGINE_OK;
    engine_volume *vols = open_volumes(env, fds, job, &count, &status);
    if (vols != NULL) {
        engine_sink s;
        status = sink_init(&s, env, sink) == 0 ? engine_extract(&s, job, vols, count, wanted, (int) n) : ENGINE_OUT_OF_MEMORY;
        free(vols);
    }
    free(wanted);
    return status;
}

static const JNINativeMethod METHODS[] = {
    {"newJob", "()J", (void *) new_job},
    {"cancel", "(J)V", (void *) cancel_job},
    {"freeJob", "(J)V", (void *) free_job},
    {"list", "(J[II" SINK_TYPE ")I", (void *) list},
    {"extract", "(J[I[J" SINK_TYPE ")I", (void *) extract},
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) reserved;
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    signal(SIGPIPE, SIG_IGN);            /* a closed pipe must be an EPIPE error, not a signal */
    setlocale(LC_CTYPE, "C.UTF-8");      /* libarchive converts names through the C locale */
    jclass clazz = (*env)->FindClass(env, ENGINE_CLASS);
    if (clazz == NULL) return JNI_ERR;
    jint registered = (*env)->RegisterNatives(env, clazz, METHODS, (jint) (sizeof METHODS / sizeof METHODS[0]));
    (*env)->DeleteLocalRef(env, clazz);
    if (registered != JNI_OK) return JNI_ERR;
    return sink_bind(env) == 0 ? JNI_VERSION_1_6 : JNI_ERR;
}
