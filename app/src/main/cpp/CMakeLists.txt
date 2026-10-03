cmake_minimum_required(VERSION 3.22.1)
project(recnote_archive LANGUAGES C CXX)
add_library(recnote_archive SHARED native_bzip2.cpp
    bzip2/blocksort.c bzip2/huffman.c bzip2/crctable.c bzip2/randtable.c
    bzip2/compress.c bzip2/decompress.c bzip2/bzlib.c)
target_include_directories(recnote_archive PRIVATE bzip2)
target_compile_features(recnote_archive PRIVATE cxx_std_17)
target_compile_options(recnote_archive PRIVATE -O3 -fvisibility=hidden)
target_link_options(recnote_archive PRIVATE "-Wl,-z,max-page-size=16384")
