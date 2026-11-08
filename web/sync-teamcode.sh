#!/usr/bin/env bash
# ===========================================================================
# Pulls the latest TeamCode and build.dependencies.gradle from
# PVI-FTC/ftc-robot-framework (default branch: feature/team-3-setup)
# and rebuilds web/harness-viewer.jar so new OpModes are available immediately.
#
# Usage:
#   bash web/sync-teamcode.sh [branch]
# ===========================================================================
set -e

BRANCH="${1:-feature/team-3-setup}"
FRAMEWORK_REPO="${FRAMEWORK_REPO:-https://github.com/PVI-FTC/ftc-robot-framework.git}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"

echo "[sync-teamcode] Fetching latest '${BRANCH}' from ${FRAMEWORK_REPO}..."
git clone --depth 1 --branch "${BRANCH}" "${FRAMEWORK_REPO}" "${TMP_DIR}"

echo "[sync-teamcode] Updating TeamCode/ and build.dependencies.gradle..."
rm -rf "${REPO_ROOT}/TeamCode/src"
cp -r "${TMP_DIR}/TeamCode/src" "${REPO_ROOT}/TeamCode/src"
cp "${TMP_DIR}/build.dependencies.gradle" "${REPO_ROOT}/build.dependencies.gradle"
rm -rf "${TMP_DIR}"

echo "[sync-teamcode] Rebuilding web/harness-viewer.jar with latest TeamCode..."
cd "${REPO_ROOT}/th_src"
chmod +x ./gradlew
./gradlew webViewerJar buildWebWrapper --rerun-tasks --no-daemon

echo "[sync-teamcode] Done! Latest TeamCode from '${BRANCH}' is now compiled into web/harness-viewer.jar."
