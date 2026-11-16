/* SPDX-License-Identifier: GPL-3.0-or-later */
#include <jni.h>
#include <signal.h>
#include <locale.h>
#include <archive.h>

/* Registers exactly the readers the app promises; links them into the .so. */
static void register_readers(struct archive *a) {
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
}

static jstring probe(JNIEnv *env, jclass clazz) {
    (void) clazz;
    struct archive *a = archive_read_new();
    register_readers(a);
    archive_read_free(a);
    return (*env)->NewStringUTF(env, archive_version_details());
}

static const JNINativeMethod METHODS[] = {
    {"probe", "()Ljava/lang/String;", (void *) probe},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) reserved;
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    signal(SIGPIPE, SIG_IGN);
    setlocale(LC_CTYPE, "C.UTF-8");
    jclass clazz = (*env)->FindClass(env, "io/github/martindavinci/lunaris/archives/engine/NativeEngine");
    if (clazz == NULL) return JNI_ERR;
    if ((*env)->RegisterNatives(env, clazz, METHODS, 1) != 0) return JNI_ERR;
    return JNI_VERSION_1_6;
}
