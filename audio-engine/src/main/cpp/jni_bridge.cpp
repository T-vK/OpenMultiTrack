#include <jni.h>

#include <optional>
#include <string>

#include "audio_player.h"
#include "audio_probe.h"
#include "audio_recorder.h"

namespace {

openmultitrack::ProbeDirection directionFromJni(jboolean isInput) {
    return isInput ? openmultitrack::ProbeDirection::Input
                   : openmultitrack::ProbeDirection::Output;
}

jstring toJstring(JNIEnv* env, const std::optional<std::string>& value) {
    if (!value.has_value()) return nullptr;
    return env->NewStringUTF(value->c_str());
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
    if (resultClass == nullptr) return nullptr;

    jmethodID ctor = env->GetMethodID(
        resultClass, "<init>", "(IIZIIIILjava/lang/String;)V");
    if (ctor == nullptr) return nullptr;

    const bool success = !result.error.has_value() && result.channelCount > 0;
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
        toJstring(env, result.error));
}

extern "C" JNIEXPORT jobject JNICALL
Java_org_openmultitrack_audio_NativeAudioEngine_nativeStartRecording(
    JNIEnv* env,
    jobject /*thiz*/,
    jint deviceId,
    jint channelCount,
    jint sampleRate) {
    const openmultitrack::RecorderStatus status =
        openmultitrack::AudioRecorder::instance().start(deviceId, channelCount, sampleRate);
    jclass cls = env->FindClass("org/openmultitrack/audio/NativeEngineStatus");
    jmethodID ctor = env->GetMethodID(cls, "<init>", "(ZIILjava/lang/String;)V");
    return env->NewObject(
        cls,
        ctor,
        static_cast<jboolean>(status.running),
        static_cast<jint>(status.channelCount),
        static_cast<jint>(status.sampleRate),
        toJstring(env, status.error));
}

extern "C" JNIEXPORT void JNICALL
Java_org_openmultitrack_audio_NativeAudioEngine_nativeStopRecording(
    JNIEnv* /*env*/,
    jobject /*thiz*/) {
    openmultitrack::AudioRecorder::instance().stop();
}

extern "C" JNIEXPORT jint JNICALL
Java_org_openmultitrack_audio_NativeAudioEngine_nativeReadRecordedFrames(
    JNIEnv* env,
    jobject /*thiz*/,
    jfloatArray dest,
    jint maxFrames) {
    jfloat* elements = env->GetFloatArrayElements(dest, nullptr);
    if (elements == nullptr) return 0;
    const size_t read = openmultitrack::AudioRecorder::instance().readFrames(
        elements, static_cast<size_t>(maxFrames));
    env->ReleaseFloatArrayElements(dest, elements, 0);
    return static_cast<jint>(read);
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_openmultitrack_audio_NativeAudioEngine_nativeRecordingDroppedFrames(
    JNIEnv* /*env*/,
    jobject /*thiz*/) {
    return static_cast<jlong>(
        openmultitrack::AudioRecorder::instance().status().droppedFrames);
}

extern "C" JNIEXPORT jobject JNICALL
Java_org_openmultitrack_audio_NativeAudioEngine_nativeStartPlayback(
    JNIEnv* env,
    jobject /*thiz*/,
    jint deviceId,
    jint channelCount,
    jint sampleRate) {
    const openmultitrack::PlayerStatus status =
        openmultitrack::AudioPlayer::instance().start(deviceId, channelCount, sampleRate);
    jclass cls = env->FindClass("org/openmultitrack/audio/NativeEngineStatus");
    jmethodID ctor = env->GetMethodID(cls, "<init>", "(ZIILjava/lang/String;)V");
    return env->NewObject(
        cls,
        ctor,
        static_cast<jboolean>(status.playing),
        static_cast<jint>(status.channelCount),
        static_cast<jint>(status.sampleRate),
        toJstring(env, status.error));
}

extern "C" JNIEXPORT void JNICALL
Java_org_openmultitrack_audio_NativeAudioEngine_nativeStopPlayback(
    JNIEnv* /*env*/,
    jobject /*thiz*/) {
    openmultitrack::AudioPlayer::instance().stop();
}

extern "C" JNIEXPORT jint JNICALL
Java_org_openmultitrack_audio_NativeAudioEngine_nativeWritePlaybackFrames(
    JNIEnv* env,
    jobject /*thiz*/,
    jfloatArray src,
    jint frameCount) {
    jfloat* elements = env->GetFloatArrayElements(src, nullptr);
    if (elements == nullptr) return 0;
    const size_t written = openmultitrack::AudioPlayer::instance().writeFrames(
        elements, static_cast<size_t>(frameCount));
    env->ReleaseFloatArrayElements(src, elements, JNI_ABORT);
    return static_cast<jint>(written);
}
