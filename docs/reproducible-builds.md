# Reproducible Builds

## Toolchain pins

- **JDK**: 17 (Temurin or distro OpenJDK)
- **Android Gradle Plugin**: 8.7.x (see `gradle/libs.versions.toml`)
- **NDK**: r26d (`android.ndkVersion` in `audio-engine/build.gradle.kts`)
- **Oboe**: git submodule tag `1.9.0` at `third_party/oboe`

## Build steps

```bash
git submodule update --init --recursive
./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release-unsigned.apk`

## F-Droid notes

- Vendored Oboe must be initialized before Gradle (recipe runs `submodules= yes` or prebuilts script).
- Use F-Droid `Builds` NDK matching `ndkVersion` for reproducibility.
- No `google-services.json`, no Play dependencies.

## Verification

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep -i google
# should return no Play Services artifacts
```

Compare APK hashes across two clean builds on same NDK/JDK versions.
