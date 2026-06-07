#pragma once

#include <android/log.h>

#define OMT_LOG_TAG "OpenMultiTrack/Audio"

#define OMT_LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, OMT_LOG_TAG, __VA_ARGS__)
#define OMT_LOGI(...) __android_log_print(ANDROID_LOG_INFO, OMT_LOG_TAG, __VA_ARGS__)
#define OMT_LOGW(...) __android_log_print(ANDROID_LOG_WARN, OMT_LOG_TAG, __VA_ARGS__)
#define OMT_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, OMT_LOG_TAG, __VA_ARGS__)
