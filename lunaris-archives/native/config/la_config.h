/* SPDX-License-Identifier: GPL-3.0-or-later
 * Fixed libarchive configuration for Lunaris Archives: bionic defaults from libarchive's own
 * contrib/android config, plus the compression libraries we build. No crypto, no iconv. */
#ifndef LUNARIS_LA_CONFIG_H
#define LUNARIS_LA_CONFIG_H
#include "../libarchive/contrib/android/config/android.h"

#define HAVE_BZLIB_H 1
#define HAVE_LIBBZ2 1
#define HAVE_LZMA_H 1
#define HAVE_LIBLZMA 1
#define LZMA_API_STATIC 1
#define HAVE_LZ4_H 1
#define HAVE_LIBLZ4 1
#define HAVE_ZSTD_H 1
#define HAVE_LIBZSTD 1
#define HAVE_ZLIB_H 1
#define HAVE_LIBZ 1
#undef HAVE_ICONV
#undef HAVE_ICONV_H
#endif
