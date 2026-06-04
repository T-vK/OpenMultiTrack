cmake_minimum_required(VERSION 3.22.1)
project(openmultitrack_audio)

set(OBOE_DIR "${CMAKE_SOURCE_DIR}/../../../../third_party/oboe")
if(NOT EXISTS "${OBOE_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "Oboe not found at ${OBOE_DIR}. Run: git submodule update --init --recursive")
endif()

# Oboe 1.9.0 treats warnings as errors; NDK r26 triggers sign-compare/unused-param.
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wno-error=unused-parameter -Wno-error=sign-compare")

add_subdirectory(${OBOE_DIR} ./oboe)

add_library(openmultitrack_audio SHARED
    audio_probe.cpp
    audio_recorder.cpp
    audio_player.cpp
    jni_bridge.cpp
)

target_include_directories(openmultitrack_audio PRIVATE
    ${OBOE_DIR}/include
)

target_link_libraries(openmultitrack_audio
    oboe
    log
)

target_compile_options(openmultitrack_audio PRIVATE -Wall -Wextra)
