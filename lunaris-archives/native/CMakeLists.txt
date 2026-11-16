# SPDX-License-Identifier: GPL-3.0-or-later
cmake_minimum_required(VERSION 3.22.1)
project(lunaris_engine LANGUAGES C)

set(CMAKE_C_STANDARD 11)
add_compile_options(
    -ffunction-sections -fdata-sections -fvisibility=hidden
    "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=."
    "-ffile-prefix-map=${CMAKE_BINARY_DIR}=build")
add_link_options(
    LINKER:--gc-sections LINKER:--exclude-libs,ALL
    LINKER:--build-id=none LINKER:--hash-style=gnu)
# NDK r27 does not align to 16 KB by default.
add_link_options(LINKER:-z,max-page-size=16384)

# zlib: system library (NDK stable API)
find_library(Z_LIBRARY z)

# bzip2
add_library(bz2 STATIC
    bzip2/blocksort.c bzip2/bzlib.c bzip2/compress.c bzip2/crctable.c
    bzip2/decompress.c bzip2/huffman.c bzip2/randtable.c)
target_include_directories(bz2 PUBLIC bzip2)

# lz4 (the decoder needs only lz4.c)
add_library(lz4 STATIC lz4/lib/lz4.c)
target_include_directories(lz4 PUBLIC lz4/lib)

# zstd (decoder only)
file(GLOB ZSTD_SOURCES zstd/lib/common/*.c zstd/lib/decompress/*.c)
add_library(zstd STATIC ${ZSTD_SOURCES})
target_compile_definitions(zstd PRIVATE ZSTD_DISABLE_ASM=1 ZSTD_LEGACY_SUPPORT=0)
target_include_directories(zstd PUBLIC zstd/lib)

# liblzma through its own CMake
set(BUILD_SHARED_LIBS OFF)
set(XZ_NLS OFF CACHE BOOL "" FORCE)
set(XZ_DOC OFF CACHE BOOL "" FORCE)
set(XZ_THREADS no CACHE STRING "" FORCE)
foreach(tool XZ_TOOL_XZ XZ_TOOL_XZDEC XZ_TOOL_LZMADEC XZ_TOOL_LZMAINFO XZ_TOOL_SCRIPTS XZ_TOOL_SYMLINKS
             XZ_TOOL_SYMLINKS_LZMA XZ_MICROLZMA_ENCODER XZ_MICROLZMA_DECODER)
  set(${tool} OFF CACHE BOOL "" FORCE)
endforeach()
add_subdirectory(xz EXCLUDE_FROM_ALL)

# libarchive: every .c except the Windows and ACL files (matches upstream's source list)
file(GLOB LA_SOURCES libarchive/libarchive/*.c)
list(FILTER LA_SOURCES EXCLUDE REGEX "(_windows|archive_disk_acl_[a-z]+|archive_entry_copy_bhfi)\\.c$")
add_library(archive STATIC ${LA_SOURCES})
target_compile_definitions(archive PRIVATE
    "PLATFORM_CONFIG_H=\"${CMAKE_CURRENT_SOURCE_DIR}/config/la_config.h\""
    LIBARCHIVE_STATIC)
target_include_directories(archive
    PUBLIC  libarchive/libarchive
    PRIVATE libarchive/contrib/android/include
            xz/src/liblzma/api)   # xz exports its API folder only on install
target_link_libraries(archive PRIVATE ${Z_LIBRARY} bz2 liblzma lz4 zstd)

# Our JNI glue
add_library(lunaris_engine SHARED
    jni/engine_jni.c)
target_compile_definitions(lunaris_engine PRIVATE _FILE_OFFSET_BITS=64)
target_include_directories(lunaris_engine PRIVATE jni)
target_compile_options(lunaris_engine PRIVATE -Wall -Wextra -Werror)
target_link_libraries(lunaris_engine PRIVATE archive ${Z_LIBRARY} log)
