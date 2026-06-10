# Getting started (developers)

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 17 | All modules target JVM 17 |
| Android SDK | API 35 | `compileSdk` / `targetSdk` |
| Android build-tools | 35.0.0 | |
| NDK | r26d (`26.3.11579264`) | Native audio + UAC2 |
| CMake | 3.22.1 | Via SDK CMake package |
| Git | — | Submodules required |

**minSdk:** 26 (AAudio device selection, USB host)

## Clone and build

```bash
git clone https://github.com/T-vK/OpenMultiTrack.git
cd OpenMultiTrack
git submodule update --init --recursive
./scripts/install-git-hooks.sh   # optional: commit-msg conventions
./gradlew :app:assembleDebug
```

The build **requires** `keystore/debug.keystore` (committed). If configuration fails, ensure the keystore file is present.

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## IDE setup

- Open the repo root in Android Studio or IntelliJ with Android plugin.
- Sync Gradle; allow NDK download if prompted (must match pinned version for reproducibility).
- For native debugging, install LLDB via SDK Manager.

## Run on device

1. Enable **USB debugging** on the Android device/tablet.
2. Connect mixer via **USB OTG** (powered hub recommended for XR18/X32).
3. Install debug APK; grant USB permission when prompted.
4. Use **Probe** or add mixer from the DAW mixer picker.

Hardware validation checklist: [../hardware-assumptions.md](../hardware-assumptions.md)

## Version and local overrides

Published version lives in `gradle/version.properties` (`VERSION_NAME`, `VERSION_CODE`).

Set locally:

```bash
./scripts/write-version-properties.sh 1.2.3
```

CI computes semver from conventional commits — see [../ci-and-releases.md](../ci-and-releases.md).

## Common commands

```bash
# Unit tests (no device)
./gradlew :domain:test :mixer-behringer:test :session-io:test \
  :usb-audio:testDebugUnitTest :remote-server:test

# Host-side UAC2 descriptor tests (C++)
./scripts/run-uac2-native-tests.sh

# Assemble androidTest APKs (device tests not run automatically)
./gradlew :app:assembleDebugAndroidTest :audio-engine:assembleDebugAndroidTest
```

## Next steps

- [codebase-map.md](codebase-map.md) — where code lives
- [conventions.md](conventions.md) — module rules and FOSS constraints
- [testing.md](testing.md) — hardware and E2E runners
- [../README.md](../README.md) — full documentation index
