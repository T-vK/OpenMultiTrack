# OpenMultiTrack — minimal rules for release shrinker.
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class org.openmultitrack.audio.NativeProbeResult { *; }
