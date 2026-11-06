#!/usr/bin/env bash
# One-time setup: points this clone's git hooks at the versioned .githooks/
# directory (git never reads .git/hooks from the repo itself, so this step
# can't be skipped - .git/hooks/pre-commit alone wouldn't survive a clone).
set -e

cd "$(git rev-parse --show-toplevel)"
git config core.hooksPath .githooks
echo "Git hooks installed (core.hooksPath=.githooks). Pre-commit will now run lint + build."
