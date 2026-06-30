#!/usr/bin/env bash
# =============================================================================
# iOS App Store publishing — CI entry point (runs on the ios-builder macOS box).
# =============================================================================
# Invoked by the android-ci-policy `publish_ios` job: that job fetches and runs
# scripts/run-publish-ios.sh from the policy repo, which execs THIS file from
# the project root. Each project owns this file (under appData/) so the
# publishing implementation stays per-project; this is the reference version
# for projects built on the kmp-sample publishing flow (scripts/publish).
#
# All configuration comes from CI/CD env vars (there is no Utils/local.properties
# checkout in CI):
#   JIRA_BASE_URL, JIRA_EMAIL, JIRA_API_TOKEN          — group CI/CD vars
#   GOOGLE_SHEETS_CREDENTIALS_JSON_BASE64              — base64 of the SA json
# The Codemagic API token and production GitHub PAT are read from the Jira
# ticket fields ("Codemagic API Token" / "GitHub Token") by the flow itself.
# =============================================================================
set -euo pipefail

# Resolve the repo root from this script's location (appData/..), independent of
# the current working directory.
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

PYTHON_BIN="${PYTHON_BIN:-python3}"
# Don't write .pyc / __pycache__ into the repo — the publish flow runs
# `git add -A`, which would otherwise sweep bytecode caches into the release.
export PYTHONDONTWRITEBYTECODE=1
# Keep the venv OUTSIDE the repo: the publish flow runs `git add -A` to stage
# its edits, and a venv inside the checkout would get committed/pushed (and trip
# up branch switches). Default to a sibling dir named for the project; override
# with PUBLISH_VENV_DIR.
VENV_DIR="${PUBLISH_VENV_DIR:-$(dirname "$REPO_ROOT")/.publish-venv-$(basename "$REPO_ROOT")}"

# Remove any stray in-repo venv left by older versions of this script so it
# can't be swept into the Release commit.
rm -rf "$REPO_ROOT/.ci-venv"

echo "==> Repo root: $REPO_ROOT"
echo "==> Creating Python venv ($VENV_DIR)"
"$PYTHON_BIN" -m venv "$VENV_DIR"
# shellcheck disable=SC1090,SC1091
source "$VENV_DIR/bin/activate"

echo "==> Installing publish dependencies"
python -m pip install --quiet --upgrade pip
python -m pip install --quiet -r scripts/requirements.txt

echo "==> Running App Store publish flow (non-interactive)"
exec python -m scripts.publish --yes
