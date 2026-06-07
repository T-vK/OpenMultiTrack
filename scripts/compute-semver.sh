#!/usr/bin/env bash
# Compute semver from conventional commits since the latest v* tag.
# Writes VERSION, TAG, VERSION_CHANGED to GITHUB_OUTPUT when set.
set -euo pipefail

TAG_PREFIX="${TAG_PREFIX:-v}"
OUTPUT_FILE="${GITHUB_OUTPUT:-}"

last_tag="$(git tag -l "${TAG_PREFIX}*" --sort=-v:refname | head -1 || true)"

if [[ -z "$last_tag" ]]; then
  major=0
  minor=0
  patch=0
  commit_range="HEAD"
else
  version="${last_tag#"$TAG_PREFIX"}"
  major="${version%%.*}"
  rest="${version#*.}"
  minor="${rest%%.*}"
  patch="${rest#*.}"
  patch="${patch%%-*}"
  commit_range="${last_tag}..HEAD"
fi

bump="none"
if [[ -n "$(git rev-list "$commit_range" 2>/dev/null | head -1 || true)" ]]; then
  mapfile -t subjects < <(git log "$commit_range" --pretty=format:%s 2>/dev/null || true)
  for subject in "${subjects[@]}"; do
    [[ -z "$subject" ]] && continue
    if [[ "$subject" == chore\(release\):* ]]; then
      continue
    fi
    if [[ "$subject" == *"BREAKING CHANGE"* ]] || [[ "$subject" == *"!"* ]]; then
      bump="major"
      break
    fi
    if [[ "$subject" == feat:* ]] || [[ "$subject" == feat\(* ]]; then
      if [[ "$bump" != "major" ]]; then
        bump="minor"
      fi
    fi
    if [[ "$subject" == fix:* ]] || [[ "$subject" == fix\(* ]]; then
      if [[ "$bump" == "none" ]]; then
        bump="patch"
      fi
    fi
  done
fi

case "$bump" in
  major) major=$((major + 1)); minor=0; patch=0 ;;
  minor) minor=$((minor + 1)); patch=0 ;;
  patch) patch=$((patch + 1)) ;;
esac

new_version="${major}.${minor}.${patch}"
new_tag="${TAG_PREFIX}${new_version}"
version_changed="false"
if [[ -z "$last_tag" ]] || [[ "$new_tag" != "$last_tag" ]]; then
  version_changed="true"
fi

echo "version=${new_version}"
echo "tag=${new_tag}"
echo "version_changed=${version_changed}"
echo "bump=${bump}"

if [[ -n "$OUTPUT_FILE" ]]; then
  {
    echo "version=${new_version}"
    echo "tag=${new_tag}"
    echo "version_changed=${version_changed}"
    echo "bump=${bump}"
  } >>"$OUTPUT_FILE"
fi
