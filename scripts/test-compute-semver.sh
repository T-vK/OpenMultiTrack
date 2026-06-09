#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
chmod +x scripts/compute-semver.sh

assert_contains() {
  local output="$1"
  local expected="$2"
  if [[ "$output" != *"$expected"* ]]; then
    echo "Expected output to contain '$expected', got:" >&2
    echo "$output" >&2
    exit 1
  fi
}

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
export GIT_DIR="$tmp/.git"
git init -q
git config user.email "test@example.com"
git config user.name "Test"
git commit --allow-empty -q -m "init"

TAG_PREFIX=v GITHUB_OUTPUT="$tmp/out" ./scripts/compute-semver.sh >/dev/null
assert_contains "$(cat "$tmp/out")" "version=0.0.1"
assert_contains "$(cat "$tmp/out")" "version_changed=true"

git tag v0.9.0
git commit --allow-empty -q -m "Fix crash when adding XR18 after Flow 8"
TAG_PREFIX=v GITHUB_OUTPUT="$tmp/out" ./scripts/compute-semver.sh >/dev/null
assert_contains "$(cat "$tmp/out")" "version=0.9.1"
assert_contains "$(cat "$tmp/out")" "version_changed=true"

git commit --allow-empty -q -m "Add channel VU meters"
TAG_PREFIX=v GITHUB_OUTPUT="$tmp/out" ./scripts/compute-semver.sh >/dev/null
assert_contains "$(cat "$tmp/out")" "version=0.10.0"
assert_contains "$(cat "$tmp/out")" "version_changed=true"

git tag v0.10.0
git commit --allow-empty -q -m "Merge pull request #11 from example/branch"
git commit --allow-empty -q -m "chore(release): v0.10.0 [skip ci]"
TAG_PREFIX=v GITHUB_OUTPUT="$tmp/out" ./scripts/compute-semver.sh >/dev/null
assert_contains "$(cat "$tmp/out")" "version=0.10.0"
assert_contains "$(cat "$tmp/out")" "version_changed=false"

echo "compute-semver tests passed"
