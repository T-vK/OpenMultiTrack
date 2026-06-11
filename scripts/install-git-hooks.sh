#!/usr/bin/env bash
# Point this repository at the shared hooks in .githooks/
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
hooks_dir="${root}/.githooks"

chmod +x "${hooks_dir}/commit-msg" "${hooks_dir}/pre-commit" "${hooks_dir}/pre-push" "${hooks_dir}/post-checkout"
git -C "$root" config core.hooksPath .githooks

echo "Installed git hooks from .githooks/"
echo "  commit-msg     — require feat: or fix: subject"
echo "  pre-commit     — commits only on main"
echo "  pre-push       — push only main to remotes"
echo "  post-checkout  — warn on non-main; block new branch checkouts"
