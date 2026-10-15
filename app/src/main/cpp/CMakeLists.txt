cmake_minimum_required(VERSION 3.22.1)
project(m120llama LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(ANDROID_ABI STREQUAL "armeabi-v7a")
    add_compile_options(-O3 -ffast-math -mfpu=neon-vfpv4)
endif()

# llama.cpp lives in the home directory on both Windows and Linux
if(WIN32)
    set(LLAMA_CPP_ROOT "$ENV{USERPROFILE}/llama.cpp")
else()
    set(LLAMA_CPP_ROOT "$ENV{HOME}/llama.cpp")
endif()

add_library(ggml-base SHARED IMPORTED)
set_target_properties(ggml-base PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/armeabi-v7a/libggml-base.so")

add_library(ggml-cpu SHARED IMPORTED)
set_target_properties(ggml-cpu PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/armeabi-v7a/libggml-cpu.so")

add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/armeabi-v7a/libggml.so")

add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/armeabi-v7a/libllama.so")

add_library(m120llama SHARED native-llama.cpp)

target_include_directories(m120llama PRIVATE
    "${LLAMA_CPP_ROOT}/include"
    "${LLAMA_CPP_ROOT}/ggml/include")

target_link_libraries(m120llama PRIVATE llama ggml ggml-cpu ggml-base log android)
