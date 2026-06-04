# CI, semantic versioning, and GitHub Pages F-Droid repo

## Workflows

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `ci.yml` | PRs, non-release pushes | Unit tests, debug APK artifact, FOSS dependency check |
| `publish.yml` | `main` push | Semver, debug APK, F-Droid index, GitHub Pages, GitHub Release |

## Semantic versioning (commit messages)

Versions follow [Conventional Commits](https://www.conventionalcommits.org/) via `PaulHatcher/semantic-version`:

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

## GitHub Pages F-Droid repository

After each successful `main` publish:

- Site: `https://T-vK.github.io/OpenMultiTrack/`
- F-Droid repo URL: `https://T-vK.github.io/OpenMultiTrack/fdroid/repo`

Add in F-Droid client → Settings → Repositories.

Optional secret `FDROID_KEYSTORE_PASS` overrides the default CI keystore password for repo signing. The signing keystore is cached between workflow runs.

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
