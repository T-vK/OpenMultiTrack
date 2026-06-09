#!/usr/bin/env bash
# Compute semver from commits since the latest v* tag.
# Recognizes conventional commits (fix:, feat:) and plain subject lines (Fix …, Add …).
# Writes version, tag, version_changed to GITHUB_OUTPUT when set.
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

should_skip_subject() {
  local subject="$1"
  [[ -z "$subject" ]] && return 0
  [[ "$subject" == chore\(release\):* ]] && return 0
  [[ "$subject" == Merge\ * ]] && return 0
  return 1
}

classify_subject_bump() {
  local subject="$1"
  if [[ "$subject" == *"BREAKING CHANGE"* ]]; then
    echo major
    return
  fi
  case "$subject" in
    *!:*|*!\(*:*)
      echo major
      return
      ;;
  esac
  case "$subject" in
    feat:*|feat\(*|Feat:*|Feat\(*|[Aa]dd*|[Ii]mplement*)
      echo minor
      return
      ;;
  esac
  case "$subject" in
    fix:*|fix\(*|Fix*)
      echo patch
      return
      ;;
  esac
  echo none
}

bump="none"
meaningful_commits=0
if [[ -n "$(git rev-list "$commit_range" 2>/dev/null | head -1 || true)" ]]; then
  mapfile -t subjects < <(git log "$commit_range" --pretty=format:%s 2>/dev/null || true)
  for subject in "${subjects[@]}"; do
    if should_skip_subject "$subject"; then
      continue
    fi
    meaningful_commits=1
    subject_bump="$(classify_subject_bump "$subject")"
    case "$subject_bump" in
      major)
        bump="major"
        break
        ;;
      minor)
        if [[ "$bump" != "major" ]]; then
          bump="minor"
        fi
        ;;
      patch)
        if [[ "$bump" == "none" ]]; then
          bump="patch"
        fi
        ;;
    esac
  done
fi

if [[ "$meaningful_commits" -eq 1 && "$bump" == "none" ]]; then
  bump="patch"
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
