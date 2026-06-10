# F-Droid and FOSS compliance

OpenMultiTrack is designed for [F-Droid](https://f-droid.org/) distribution. **Every dependency, asset, and build input must be compatible with GPLv3-or-later and F-Droid policy.** This is enforced in code review, Cursor rules, and CI.

Official references:

- [F-Droid inclusion policy](https://f-droid.org/docs/Inclusion_Policy/)
- [License categories F-Droid accepts](https://f-droid.org/docs/Inclusion_Policy/#license)

---

## Hard requirements

| Requirement | Project rule |
|-------------|--------------|
| **Free Software** | App is GPLv3-or-later; all shipped code buildable from source in this repo |
| **No proprietary blobs** | No prebuilt `.so`, `.jar`, or SDK binaries in app source — Oboe (submodule) and libusb (vendored source) are compiled in-tree |
| **No Google proprietary stack** | No Play Services, Firebase, GMS, Crashlytics, or Play-feature modules |
| **No tracking / ads** | No analytics, ad SDKs, or telemetry that phones home |
| **FOSS dependencies only** | Libraries must use licenses compatible with GPLv3 in an Android APK (see table below) |
| **No external CDNs** | Bundled web assets (if any) must ship inside the APK — no runtime load from third-party URLs |
| **Reproducible intent** | Toolchain pins documented in [reproducible-builds.md](../reproducible-builds.md) |

---

## Allowed dependency licenses (typical)

| License | OK for this project? |
|---------|----------------------|
| Apache-2.0 | ✅ (AndroidX, Oboe, Ktor-class libs) |
| MIT, BSD-2/3-Clause | ✅ |
| ISC | ✅ |
| LGPL-2.1+ (libusb, dynamically linked) | ✅ with source vendored — we build from source |
| GPL-2.0+ / GPL-3.0+ | ✅ (our app license; compatible deps) |
| AGPL-3.0 | ⚠️ Review copyleft interaction before adding |
| **Proprietary / unknown / “free for dev”** | ❌ **Never** |
| **CC-NC, “personal use only” assets** | ❌ **Never** |

When adding a dependency, confirm license in `LICENSE` or `NOTICE` in the upstream repo and in `gradle/libs.versions.toml` / module `build.gradle.kts`.

---

## Forbidden (explicit)

Do **not** add without explicit maintainer approval:

- `com.google.android.gms:*`, `com.google.firebase:*`, `play-services-*`
- Proprietary USB/audio SDKs (Behringer, Steinberg, etc.)
- Crash reporters that upload to vendor clouds (Sentry proprietary, Bugsnag, etc.)
- Analytics (Firebase Analytics, Mixpanel, Amplitude, …)
- Ad networks
- Non-free fonts, icons, or sample audio without clear FOSS license
- Maven artifacts that bundle native `.so` without corresponding source in repo or submodule

CI grep (`.github/workflows/ci.yml`):

```text
com.google.android.gms | firebase | play-services
```

Extend the grep if a new forbidden family is introduced.

---

## Native code

| Component | Source | License |
|-----------|--------|---------|
| Oboe | `third_party/oboe` git submodule | Apache-2.0 |
| libusb | `third_party/libusb` vendored sources | LGPL-2.1 |

Run `git submodule update --init --recursive` before build. Do not replace with prebuilt NDK blobs from vendors.

---

## Signing and release (F-Droid context)

- Published builds today use **committed debug keystore** (`keystore/debug.keystore`) — same APK on GitHub Releases and self-hosted F-Droid repo.
- Official F-Droid **main** inclusion may require release signing and reproducible build verification — see [PROJECT_STATUS.md](../PROJECT_STATUS.md) milestone M6.

---

## Checklist before adding a dependency

1. Is the license FOSS and GPLv3-compatible?
2. Is full source available without registration?
3. Does it pull Play Services, Firebase, or ads transitively? (`./gradlew :app:dependencies`)
4. Does it download assets or code at runtime from the internet?
5. Update docs if the dependency is architecturally significant.

---

## Related

- [conventions.md](conventions.md) — short FOSS table
- [CONTRIBUTING.md](../../CONTRIBUTING.md)
- [ci-and-releases.md](../ci-and-releases.md)
- Cursor rule: `.cursor/rules/foss-fdroid.mdc`
