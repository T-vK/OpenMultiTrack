#include "uac2_capture.h"
#include "uac2_playback.h"

#include <jni.h>

#include <optional>
#include <string>

namespace {

openmultitrack::uac2::Uac2AltSetting altFromJobject(JNIEnv* env, jobject altObj) {
    openmultitrack::uac2::Uac2AltSetting alt;
    if (altObj == nullptr) return alt;

    jclass cls = env->GetObjectClass(altObj);
    alt.interface_number = static_cast<uint8_t>(env->GetIntField(
        altObj, env->GetFieldID(cls, "interfaceNumber", "I")));
    alt.alternate_setting = static_cast<uint8_t>(env->GetIntField(
        altObj, env->GetFieldID(cls, "alternateSetting", "I")));
    alt.endpoint_address = static_cast<uint8_t>(env->GetIntField(
        altObj, env->GetFieldID(cls, "endpointAddress", "I")));
    alt.max_packet_size = static_cast<uint16_t>(env->GetIntField(
        altObj, env->GetFieldID(cls, "maxPacketSize", "I")));
    alt.is_input = env->GetBooleanField(altObj, env->GetFieldID(cls, "isInput", "Z"));
    alt.format.channels = static_cast<uint8_t>(env->GetIntField(
        altObj, env->GetFieldID(cls, "channels", "I")));
    alt.format.sample_rate_hz = static_cast<uint32_t>(env->GetIntField(
        altObj, env->GetFieldID(cls, "sampleRateHz", "I")));
    alt.format.bit_resolution = static_cast<uint8_t>(env->GetIntField(
        altObj, env->GetFieldID(cls, "bitResolution", "I")));
    alt.format.subframe_bytes = static_cast<uint8_t>(env->GetIntField(
        altObj, env->GetFieldID(cls, "subframeBytes", "I")));
    alt.format.valid = env->GetBooleanField(altObj, env->GetFieldID(cls, "formatValid", "Z"));
    return alt;
}

jstring toJstring(JNIEnv* env, const std::optional<std::string>& value) {
    if (!value.has_value()) return nullptr;
    return env->NewStringUTF(value->c_str());
}

jobject makeEngineStatus(JNIEnv* env, bool active, jint channels, jint sampleRate, jstring error) {
    jclass cls = env->FindClass("org/openmultitrack/audio/NativeEngineStatus");
    if (cls == nullptr) return nullptr;
    jmethodID ctor = env->GetMethodID(cls, "<init>", "(ZIILjava/lang/String;)V");
    if (ctor == nullptr) return nullptr;
    return env->NewObject(cls, ctor, static_cast<jboolean>(active), channels, sampleRate, error);
}

}  // namespace

extern "C" JNIEXPORT jobject JNICALL
Java_org_openmultitrack_audio_NativeUac2Engine_nativeStartCapture(
    JNIEnv* env,
    jobject /*thiz*/,
    jint usbFd,
    jobject altObj,
    jboolean javaInterfaceClaimed) {
    const openmultitrack::uac2::Uac2AltSetting alt = altFromJobject(env, altObj);
    const openmultitrack::uac2::CaptureStatus status =
        openmultitrack::uac2::Uac2Capture::instance().open(
            usbFd, alt, javaInterfaceClaimed == JNI_TRUE);
    return makeEngineStatus(
        env,
        status.running,
        status.channel_count,
        status.sample_rate,
        toJstring(env, status.error));
}

extern "C" JNIEXPORT void JNICALL
Java_org_openmultitrack_audio_NativeUac2Engine_nativeStopCapture(
    JNIEnv* /*env*/,
    jobject /*thiz*/) {
    openmultitrack::uac2::Uac2Capture::instance().close();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_openmultitrack_audio_NativeUac2Engine_nativeIsCaptureRunning(
    JNIEnv* /*env*/,
    jobject /*thiz*/) {
    return openmultitrack::uac2::Uac2Capture::instance().isRunning() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_openmultitrack_audio_NativeUac2Engine_nativeReadCapturedFrames(
    JNIEnv* env,
    jobject /*thiz*/,
    jfloatArray dest,
    jint maxFrames) {
    jfloat* elements = static_cast<jfloat*>(env->GetPrimitiveArrayCritical(dest, nullptr));
    if (elements == nullptr) return 0;
    const size_t read = openmultitrack::uac2::Uac2Capture::instance().readFrames(
        elements, static_cast<size_t>(maxFrames));
    env->ReleasePrimitiveArrayCritical(dest, elements, 0);
    return static_cast<jint>(read);
}

extern "C" JNIEXPORT jint JNICALL
Java_org_openmultitrack_audio_NativeUac2Engine_nativeReadCapturedPcmBytes(
    JNIEnv* env,
    jobject /*thiz*/,
    jbyteArray dest,
    jint maxFrames) {
    jbyte* elements = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(dest, nullptr));
    if (elements == nullptr) return 0;
    const int32_t bpf =
        static_cast<int32_t>(openmultitrack::uac2::Uac2Capture::instance().captureBytesPerFrame());
    jint read = 0;
    if (bpf > 0) {
        const jsize required = static_cast<jsize>(
            static_cast<int64_t>(maxFrames) * static_cast<int64_t>(bpf));
        if (env->GetArrayLength(dest) < required) {
            env->ReleasePrimitiveArrayCritical(dest, elements, JNI_ABORT);
            return 0;
        }
    }
    const size_t frames = openmultitrack::uac2::Uac2Capture::instance().readPcmBytes(
        reinterpret_cast<uint8_t*>(elements),
        static_cast<size_t>(maxFrames));
    read = static_cast<jint>(frames);
    env->ReleasePrimitiveArrayCritical(dest, elements, 0);
    return read;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_openmultitrack_audio_NativeUac2Engine_nativeCaptureBytesPerFrame(
    JNIEnv* /*env*/,
    jobject /*thiz*/) {
    return static_cast<jint>(
        openmultitrack::uac2::Uac2Capture::instance().captureBytesPerFrame());
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_openmultitrack_audio_NativeUac2Engine_nativeCaptureDroppedFrames(
    JNIEnv* /*env*/,
    jobject /*thiz*/) {
    return static_cast<jlong>(openmultitrack::uac2::Uac2Capture::instance().droppedFrames());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_openmultitrack_audio_NativeUac2Engine_nativeStartPcmFileRecording(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring path) {
    const char* utf = env->GetStringUTFChars(path, nullptr);
    if (utf == nullptr) return JNI_FALSE;
    const bool ok = openmultitrack::uac2::Uac2Capture::instance().startPcmFileRecording(utf);
    env->ReleaseStringUTFChars(path, utf);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_org_openmultitrack_audio_NativeUac2Engine_nativeStopPcmFileRecording(
    JNIEnv* /*env*/,
    jobject /*thiz*/) {
    openmultitrack::uac2::Uac2Capture::instance().stopPcmFileRecording();
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_openmultitrack_audio_NativeUac2Engine_nativePcmFileFramesWritten(
    JNIEnv* /*env*/,
    jobject /*thiz*/) {
    return static_cast<jlong>(
        openmultitrack::uac2::Uac2Capture::instance().pcmFileFramesWritten());
}

extern "C" JNIEXPORT jobject JNICALL
Java_org_openmultitrack_audio_NativeUac2Engine_nativeStartPlayback(
    JNIEnv* env,
    jobject /*thiz*/,
    jint usbFd,
    jobject altObj,
    jboolean javaInterfaceClaimed) {
    const openmultitrack::uac2::Uac2AltSetting alt = altFromJobject(env, altObj);
    const openmultitrack::uac2::PlaybackStatus status =
        openmultitrack::uac2::Uac2Playback::instance().open(
            usbFd, alt, javaInterfaceClaimed == JNI_TRUE);
    return makeEngineStatus(
        env,
        status.running,
        status.channel_count,
        status.sample_rate,
        toJstring(env, status.error));
}

extern "C" JNIEXPORT void JNICALL
Java_org_openmultitrack_audio_NativeUac2Engine_nativeStopPlayback(
    JNIEnv* /*env*/,
    jobject /*thiz*/) {
    openmultitrack::uac2::Uac2Playback::instance().close();
}

extern "C" JNIEXPORT jint JNICALL
Java_org_openmultitrack_audio_NativeUac2Engine_nativeWritePlaybackFrames(
    JNIEnv* env,
    jobject /*thiz*/,
    jfloatArray src,
    jint frameCount) {
    jfloat* elements = env->GetFloatArrayElements(src, nullptr);
    if (elements == nullptr) return 0;
    const size_t written = openmultitrack::uac2::Uac2Playback::instance().writeFrames(
        elements, static_cast<size_t>(frameCount));
    env->ReleaseFloatArrayElements(src, elements, JNI_ABORT);
    return static_cast<jint>(written);
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_openmultitrack_audio_NativeUac2Engine_nativePlaybackUnderrunFrames(
    JNIEnv* /*env*/,
    jobject /*thiz*/) {
    return static_cast<jlong>(openmultitrack::uac2::Uac2Playback::instance().underrunFrames());
}
