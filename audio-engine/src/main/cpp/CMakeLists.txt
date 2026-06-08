cmake_minimum_required(VERSION 3.22.1)
project(openmultitrack_audio)

set(OBOE_DIR "${CMAKE_SOURCE_DIR}/../../../../third_party/oboe")
if(NOT EXISTS "${OBOE_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "Oboe not found at ${OBOE_DIR}. Run: git submodule update --init --recursive")
endif()

set(LIBUSB_DIR "${CMAKE_SOURCE_DIR}/../../../../third_party/libusb")
if(NOT EXISTS "${LIBUSB_DIR}/libusb/libusb.h")
    message(FATAL_ERROR "libusb not found at ${LIBUSB_DIR}. Clone libusb into third_party/libusb")
endif()

# Oboe 1.9.0 treats warnings as errors; NDK r26 triggers sign-compare/unused-param.
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wno-error=unused-parameter -Wno-error=sign-compare")

add_subdirectory(${OBOE_DIR} ./oboe)

set(LIBUSB_SOURCES
    ${LIBUSB_DIR}/libusb/core.c
    ${LIBUSB_DIR}/libusb/descriptor.c
    ${LIBUSB_DIR}/libusb/hotplug.c
    ${LIBUSB_DIR}/libusb/io.c
    ${LIBUSB_DIR}/libusb/sync.c
    ${LIBUSB_DIR}/libusb/strerror.c
    ${LIBUSB_DIR}/libusb/os/linux_usbfs.c
    ${LIBUSB_DIR}/libusb/os/events_posix.c
    ${LIBUSB_DIR}/libusb/os/threads_posix.c
)

add_library(usb-1.0 STATIC ${LIBUSB_SOURCES})
target_include_directories(usb-1.0 PUBLIC
    ${LIBUSB_DIR}/libusb
    ${CMAKE_CURRENT_SOURCE_DIR}/uac2
)
target_compile_options(usb-1.0 PRIVATE
    -fvisibility=hidden
    -Wno-unused-parameter
)

add_library(openmultitrack_audio SHARED
    audio_probe.cpp
    audio_recorder.cpp
    audio_player.cpp
    audio_monitor.cpp
    jni_bridge.cpp
    uac2/android_usb_io.cpp
    uac2/libusb_session.cpp
    uac2/uac2_capture.cpp
    uac2/uac2_descriptor.cpp
    uac2/uac2_engine_jni.cpp
    uac2/uac2_format.cpp
    uac2/uac2_jni.cpp
    uac2/uac2_playback.cpp
)

target_include_directories(openmultitrack_audio PRIVATE
    ${OBOE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${LIBUSB_DIR}/libusb
)

target_link_libraries(openmultitrack_audio
    oboe
    usb-1.0
    log
)

target_compile_options(openmultitrack_audio PRIVATE -Wall -Wextra)
