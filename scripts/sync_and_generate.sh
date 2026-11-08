#!/usr/bin/env bash
# ==============================================================================
# One-Touch Automated Pipeline: Sync, Auto-Calibrate, & Generate 3D Recordings
#
# Usage:
#   bash scripts/sync_and_generate.sh [branch-name]
#
# Example:
#   bash scripts/sync_and_generate.sh feature/team-3-setup
#   bash scripts/sync_and_generate.sh Team2-Robot-Code
#   bash scripts/sync_and_generate.sh master
# ==============================================================================
set -e

BRANCH="${1:-feature/team-3-setup}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UPSTREAM_REPO="${UPSTREAM_REPO:-https://github.com/PVI-FTC/ftc-robot-framework.git}"
TMP_DIR="$(mktemp -d)"

if [ -d "/Users/elliott/jdk17/Contents/Home" ]; then
  export JAVA_HOME="/Users/elliott/jdk17/Contents/Home"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

echo "======================================================================"
echo " [FTC SIM] Auto-Sync & Simulation Pipeline for Branch: ${BRANCH}"
echo "======================================================================"

# 1. Fetch latest upstream TeamCode
echo "[1/4] Cloning latest '${BRANCH}' from ${UPSTREAM_REPO}..."
git clone --depth 1 --branch "${BRANCH}" "${UPSTREAM_REPO}" "${TMP_DIR}"

echo "[2/4] Updating local TeamCode..."
rm -rf "${REPO_ROOT}/TeamCode/src"
cp -r "${TMP_DIR}/TeamCode/src" "${REPO_ROOT}/TeamCode/src"
cp "${TMP_DIR}/build.dependencies.gradle" "${REPO_ROOT}/build.dependencies.gradle"
rm -rf "${TMP_DIR}"

# 2. Run Auto-Calibration on code
echo "[3/4] Running Auto-Calibration Engine to extract robot physics from code..."
python3 "${REPO_ROOT}/scripts/auto_calibrate.py"

# Detect team id from branch
TEAM_ID="team3"
if [[ "${BRANCH}" =~ [Tt]eam[-_]?2 ]]; then
  TEAM_ID="team2"
elif [[ "${BRANCH}" =~ [Tt]eam[-_]?4 ]]; then
  TEAM_ID="team4"
elif [[ "${BRANCH}" =~ [Tt]eam[-_]?[Aa] ]] || [[ "${BRANCH}" == "master" ]]; then
  TEAM_ID="teamA"
fi

if [ -f "${REPO_ROOT}/configs/teams/${TEAM_ID}.properties" ]; then
  echo "      Activating team profile: ${TEAM_ID}"
  cp "${REPO_ROOT}/configs/teams/${TEAM_ID}.properties" "${REPO_ROOT}/th_src/harness.properties"
  cp "${REPO_ROOT}/configs/teams/${TEAM_ID}.properties" "${REPO_ROOT}/web/harness.properties"
fi

# 3. Headless simulation run
echo "[4/4] Simulating all autonomous OpModes & generating 3D WebGL recordings..."
cd "${REPO_ROOT}/th_src"
chmod +x ./gradlew
./gradlew test --tests "pvi.harness.GenerateTeam3AutonomousRecordingsTest" --rerun-tasks --no-daemon
./gradlew buildWebWrapper --no-daemon

cp build/recordings/auto-*.json "${REPO_ROOT}/web/vi_arena/recordings/" 2>/dev/null || true
cp build/recordings/auto-*.json "${REPO_ROOT}/web/build/recordings/" 2>/dev/null || true

echo "======================================================================"
echo " [SUCCESS] 3D Autonomous runs simulated and ready in web/vi_arena/!"
echo "======================================================================"

