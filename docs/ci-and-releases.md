# CI, semantic versioning, and GitHub Pages F-Droid repo

## Workflows

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `ci.yml` | PRs, non-release pushes | Unit tests, debug APK artifact, FOSS dependency check |
| `publish.yml` | `main` push | Three jobs: **build** → **pages** (F-Droid + GitHub Pages) + **release** (parallel) |

## CI caching

The composite action `.github/actions/android-setup` caches:

| Cache | Key inputs | Saves |
|-------|------------|-------|
| Gradle | `gradle/actions/setup-gradle` — dependencies, wrapper, build cache | Restored/saved automatically per job |
| Android SDK | API 35, NDK r26d, build-tools 35 | NDK, CMake, platforms (~minutes on miss) |
| Native CXX | `audio-engine` sources, Oboe headers | `audio-engine/.cxx` + `intermediates/cxx` |
| pip | `scripts/requirements-fdroid.txt` | fdroidserver (publish only) |
| F-Droid repo | prior `fdroid/repo` contents | publish workflow only |

Gradle build cache is enabled via `org.gradle.caching=true` and `--build-cache` on CI invocations.
The publish **pages** job skips Android SDK setup (APK is verified in the build job).

## Icons

Regenerate launcher, fastlane, and F-Droid repo icons from `iconv2.png` (centered artwork on a circular background for adaptive-icon safe zones):

```bash
python3 scripts/generate-branding-icons.py
```

The publish workflow runs `scripts/prepare-fdroid-icons.sh` before `fdroid update` so the
repository icon (`fdroid/repo/icons/icon.png`) and app metadata icon are staged for F-Droid.

## Semantic versioning (commit messages)

Versions follow [Conventional Commits](https://www.conventionalcommits.org/) via [`scripts/compute-semver.sh`](../scripts/compute-semver.sh) (no third-party action):

| Commit prefix | Bump |
|---------------|------|
| `feat:` | minor |
| `fix:` | patch |
| `BREAKING CHANGE` or `!:` in subject | major |

Examples:

```
feat: add 18-channel recording
fix: USB detach during record
feat!: change session file layout
```

`gradle/version.properties` is updated on release; `VERSION_CODE = major*10000 + minor*100 + patch`.

Plain subjects such as `Fix crash when adding XR18` are also recognized (see `scripts/compute-semver.sh`).

## Git hooks (local)

After cloning, install the shared hooks once:

```bash
./scripts/install-git-hooks.sh
```

| Hook | Rule |
|------|------|
| `commit-msg` | Subject must start with `feat: ` or `fix: ` |
| `pre-commit` | Reject commits on `cursor/*` branches |
| `pre-push` | Reject pushing `cursor/*` branches |

Commit directly to `main`; do not use `cursor/*` feature branches or pull requests.

## GitHub Pages F-Droid repository

After each successful `main` publish:

- Site: `https://T-vK.github.io/OpenMultiTrack/`
- F-Droid repo URL: `https://T-vK.github.io/OpenMultiTrack/fdroid/repo`

Add in F-Droid client → Settings → Repositories.

Optional secret `FDROID_KEYSTORE_PASS` overrides the default CI keystore password for repo signing. The signing keystore is cached between workflow runs.

**One debug APK per release** — the `build` job produces a single `assembleDebug` artifact. The `pages` job publishes it to the F-Droid repo; the `release` job attaches the same file to the GitHub Release (plus `SHA256SUMS`). There is no separate release build type.

App metadata lives in `fdroid/metadata/org.openmultitrack.yml`. CI updates `CurrentVersionCode` before `fdroid update` so the client’s `suggestedVersionCode` matches a real APK.

**Stable debug signing** — `keystore/debug.keystore` is committed to the repo (debug-only, not a Play Store key). Every CI and local debug build uses it so in-place upgrades work. The expected certificate fingerprint is in `keystore/EXPECTED_SIGNER.txt`; `scripts/verify-apk-signature.sh` runs in the publish `build` job before upload.

If you installed a build from before signing was pinned (≤0.2.1), uninstall once and reinstall from the F-Droid repo or GitHub Release. Later updates install in place.

## Local build

```bash
git submodule update --init --recursive
./scripts/write-version-properties.sh 1.2.3
./gradlew :app:assembleDebug
```

## Initial tag

If no `v*` tag exists, the first `feat:`/`fix:` on `main` establishes the next version from commit history. Tag `v0.1.0` manually to pin the baseline:

```bash
git tag v0.1.0 && git push origin v0.1.0
```
