#include <jni.h>

#include "audio_probe.h"

namespace {

openmultitrack::ProbeDirection directionFromJni(jboolean isInput) {
    return isInput ? openmultitrack::ProbeDirection::Input
                   : openmultitrack::ProbeDirection::Output;
}

}  // namespace

extern "C" JNIEXPORT jobject JNICALL
Java_org_openmultitrack_audio_NativeAudioProbe_nativeProbe(
    JNIEnv* env,
    jobject /*thiz*/,
    jint deviceId,
    jboolean isInput) {
    const openmultitrack::ProbeResult result =
        openmultitrack::probeUsbAudioEndpoint(deviceId, directionFromJni(isInput));

    jclass resultClass = env->FindClass("org/openmultitrack/audio/NativeProbeResult");
    if (resultClass == nullptr) {
        return nullptr;
    }

    jmethodID ctor = env->GetMethodID(
        resultClass,
        "<init>",
        "(IIZIIIILjava/lang/String;)V");
    if (ctor == nullptr) {
        return nullptr;
    }

    const bool success = !result.error.has_value() && result.channelCount > 0;
    jstring error = nullptr;
    if (result.error.has_value()) {
        error = env->NewStringUTF(result.error->c_str());
    }

    return env->NewObject(
        resultClass,
        ctor,
        static_cast<jint>(result.deviceId),
        static_cast<jint>(isInput ? 0 : 1),
        static_cast<jboolean>(success),
        static_cast<jint>(result.channelCount),
        static_cast<jint>(result.sampleRate),
        static_cast<jint>(result.framesPerBurst),
        static_cast<jint>(0),
        error);
}
