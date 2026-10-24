cmake_minimum_required(VERSION 3.22.1)
project(cherry_audio C)
add_library(cherry_audio SHARED cherry_denoiser.c cherry_jni.c
    speexdsp/libspeexdsp/preprocess.c speexdsp/libspeexdsp/mdf.c
    speexdsp/libspeexdsp/fftwrap.c speexdsp/libspeexdsp/filterbank.c speexdsp/libspeexdsp/smallft.c)
target_include_directories(cherry_audio PRIVATE speexdsp/include speexdsp/libspeexdsp)
target_compile_definitions(cherry_audio PRIVATE FLOATING_POINT USE_SMALLFT EXPORT=)
target_link_libraries(cherry_audio m)

target_link_options(cherry_audio PRIVATE "-Wl,-z,max-page-size=16384")
