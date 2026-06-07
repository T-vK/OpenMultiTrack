#include "uac2_descriptor.h"

#include <jni.h>

#include <vector>

namespace {

jclass gCapsClass = nullptr;
jclass gAltClass = nullptr;
jmethodID gCapsCtor = nullptr;
jmethodID gAltCtor = nullptr;

jobject makeAlt(JNIEnv* env, const openmultitrack::uac2::Uac2AltSetting& alt) {
    if (gAltClass == nullptr || gAltCtor == nullptr) return nullptr;
    return env->NewObject(
        gAltClass,
        gAltCtor,
        static_cast<jint>(alt.interface_number),
        static_cast<jint>(alt.alternate_setting),
        static_cast<jint>(alt.endpoint_address),
        static_cast<jint>(alt.max_packet_size),
        static_cast<jboolean>(alt.is_input),
        static_cast<jint>(alt.format.channels),
        static_cast<jint>(alt.format.sample_rate_hz),
        static_cast<jint>(alt.format.bit_resolution),
        static_cast<jint>(alt.format.subframe_bytes),
        static_cast<jboolean>(alt.format.valid));
}

jobjectArray makeAltArray(JNIEnv* env, const std::vector<openmultitrack::uac2::Uac2AltSetting>& alts) {
    if (gAltClass == nullptr) return nullptr;
    jobjectArray arr = env->NewObjectArray(
        static_cast<jsize>(alts.size()), gAltClass, nullptr);
    if (arr == nullptr) return nullptr;
    for (jsize i = 0; i < static_cast<jsize>(alts.size()); ++i) {
        jobject alt = makeAlt(env, alts[static_cast<size_t>(i)]);
        if (alt == nullptr) return nullptr;
        env->SetObjectArrayElement(arr, i, alt);
        env->DeleteLocalRef(alt);
    }
    return arr;
}

bool initClasses(JNIEnv* env) {
    if (gCapsClass != nullptr) return true;

    jclass capsLocal = env->FindClass("org/openmultitrack/audio/NativeUac2DeviceCaps");
    jclass altLocal = env->FindClass("org/openmultitrack/audio/NativeUac2AltSetting");
    if (capsLocal == nullptr || altLocal == nullptr) return false;

    gCapsClass = reinterpret_cast<jclass>(env->NewGlobalRef(capsLocal));
    gAltClass = reinterpret_cast<jclass>(env->NewGlobalRef(altLocal));
    env->DeleteLocalRef(capsLocal);
    env->DeleteLocalRef(altLocal);

    gCapsCtor = env->GetMethodID(
        gCapsClass,
        "<init>",
        "(I[Lorg/openmultitrack/audio/NativeUac2AltSetting;"
        "[Lorg/openmultitrack/audio/NativeUac2AltSetting;Z)V");
    gAltCtor = env->GetMethodID(
        gAltClass,
        "<init>",
        "(IIIIZIIIIZ)V");
    return gCapsCtor != nullptr && gAltCtor != nullptr;
}

}  // namespace

extern "C" JNIEXPORT jobject JNICALL
Java_org_openmultitrack_audio_NativeUac2Probe_nativeParseConfigDescriptor(
    JNIEnv* env,
    jobject /*thiz*/,
    jbyteArray raw) {
    if (!initClasses(env)) return nullptr;

    const jsize len = env->GetArrayLength(raw);
    if (len <= 0) return nullptr;

    std::vector<uint8_t> bytes(static_cast<size_t>(len));
    env->GetByteArrayRegion(raw, 0, len, reinterpret_cast<jbyte*>(bytes.data()));

    const openmultitrack::uac2::Uac2DeviceCaps caps =
        openmultitrack::uac2::parseConfigDescriptor(bytes.data(), bytes.size());

    jobjectArray capture = makeAltArray(env, caps.capture_alts);
    jobjectArray playback = makeAltArray(env, caps.playback_alts);
    if (capture == nullptr || playback == nullptr) return nullptr;

    return env->NewObject(
        gCapsClass,
        gCapsCtor,
        static_cast<jint>(caps.uac_version),
        capture,
        playback,
        static_cast<jboolean>(caps.parse_ok));
}

extern "C" JNIEXPORT jobject JNICALL
Java_org_openmultitrack_audio_NativeUac2Probe_nativeSelectBestAlt(
    JNIEnv* env,
    jobject /*thiz*/,
    jobjectArray alts,
    jint minChannels,
    jint sampleRateHz) {
    if (!initClasses(env) || alts == nullptr) return nullptr;

    std::vector<openmultitrack::uac2::Uac2AltSetting> list;
    const jsize count = env->GetArrayLength(alts);
    list.reserve(static_cast<size_t>(count));

    for (jsize i = 0; i < count; ++i) {
        jobject altObj = env->GetObjectArrayElement(alts, i);
        if (altObj == nullptr) continue;

        openmultitrack::uac2::Uac2AltSetting alt;
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

        list.push_back(alt);
        env->DeleteLocalRef(altObj);
    }

    const openmultitrack::uac2::Uac2AltSetting best = openmultitrack::uac2::selectBestAlt(
        list,
        static_cast<uint8_t>(minChannels),
        static_cast<uint32_t>(sampleRateHz));

    if (!best.format.valid) return nullptr;
    return makeAlt(env, best);
}
