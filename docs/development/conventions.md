# Development conventions

Contributor onboarding summary: [../../CONTRIBUTING.md](../../CONTRIBUTING.md)

## Module boundaries

1. **`domain` is pure Kotlin** — no `android.*`, no JNI, no networking implementations.
2. **Dependency direction** — `app` → libraries; never `domain` → `app`.
3. **Audio real-time path stays in native** — Kotlin drains/fills rings on dedicated threads only.
4. **OSC and remote protocols** — encode/decode in their modules; business rules in `domain` types.

## FOSS and compliance (non-negotiable)

**Full guide:** [fdroid-compliance.md](fdroid-compliance.md) · **Cursor rule:** `.cursor/rules/foss-fdroid.mdc`

| Rule | Enforcement |
|------|-------------|
| No Play Services / Firebase / GMS / ads / telemetry | CI greps `:app:dependencies` |
| FOSS-only dependencies | Apache-2.0, MIT, BSD, GPL-compatible |
| No binary blobs in app source | Oboe + libusb built from source/submodule |
| No external CDNs in bundled assets | Self-contained APK |
| License GPLv3-or-later | See root `LICENSE` |

Do not add proprietary USB SDKs or crash reporting that phones home.

## Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org/) for `main` publishes:

| Prefix | Semver bump |
|--------|-------------|
| `feat:` | minor |
| `fix:` | patch |
| `BREAKING CHANGE` or `!:` in subject | major |

Install hooks: `./scripts/install-git-hooks.sh`

## Code style

- Match surrounding Kotlin/C++ style in each file.
- Prefer extending existing types over parallel abstractions.
- Comments only for non-obvious protocol or real-time constraints.
- No drive-by refactors in unrelated modules.

## Naming

| Concept | Convention |
|---------|------------|
| Mixer instance id | Stable string (serial, or IP+model) |
| Session directory | `{storageRoot}/{MixerFolder}/{yyyy-MM-dd-HH-mm-ss}/` |
| Channel files | `channelNN.wav` or `channelNN - Label.wav` |
| Package root | `org.openmultitrack.<module>` |

## Adding a feature (checklist)

1. Identify the owning module ([codebase-map.md](codebase-map.md)).
2. Add domain types first if shared by UI and remote.
3. Unit test in the lowest layer (`domain`, `session-io`, `mixer-behringer`).
4. Update [PROJECT_STATUS.md](../PROJECT_STATUS.md) if user-visible status changes.
5. Update relevant architecture/product doc if behavior or protocol changes.
6. Flag new hardware assumptions in [hardware-assumptions.md](../hardware-assumptions.md).

## Documentation

- User-facing install/build → root [README.md](../../README.md)
- Developer docs → [docs/README.md](../README.md)
- Avoid line numbers in docs; reference packages, types, and file paths.
- Supersede rather than duplicate — link to the canonical doc.

## CI expectations

PRs (`ci.yml`): unit tests, UAC2 native tests, `assembleDebug`, FOSS grep.

`main` (`publish.yml`): semver bump, F-Droid repo, GitHub Release when version changes.

Details: [../ci-and-releases.md](../ci-and-releases.md)
